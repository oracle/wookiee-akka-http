/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.component.akkahttp.websocket

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.zip.{DeflaterOutputStream, GZIPOutputStream}

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.http.javadsl.model.headers.{AcceptEncoding, ContentEncoding, HttpEncodingRange}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.{HttpEncoding, HttpEncodings}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.{Command, MapBean}
import com.webtrends.harness.component.akkahttp.routes.WebsocketAkkaHttpRouteContainer
import com.webtrends.harness.component.akkahttp.{AkkaHttpSettings}
import com.webtrends.harness.component.metrics.metrictype.Counter

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


trait AkkaHttpWebsocket extends HActor {
  val supported = List(HttpEncodings.gzip, HttpEncodings.deflate)
  val settings = AkkaHttpSettings(config)
  val openSocketGauge = Counter(s"wookiee.akka-http.websocket.${path.replaceAll("/", "-")}.open-count")

  // Standard overrides
  // Can be implemented if text is desired to be streamed (must override isStreamingText = true)
  def handleTextStream(ts: Source[String, _], bean: MapBean, callback: ActorRef): Option[TextMessage] = {
    throw new NotImplementedError("Must override isStreamingText and handleTextStream to handle streaming text.")
  }

  // Main handler for incoming messages
  def handleText(text: String, bean: MapBean, callback: ActorRef): Option[TextMessage]

  // Override if you want the TextMessage content to be left as a stream
  def isStreamingText: Boolean = false

  // Should we keep this websocket alive with heartbeat messages, will cause us to send a TextMessage("heartbeat")
  // back to the client every 30 seconds, override with true if you want just this websocket to do it
  def keepAliveOn: Boolean = settings.ws.keepAliveOn

  // Override for websocket closure code, callback will be None if we aren't connected yet
  def onWebsocketClose(bean: MapBean, callback: Option[ActorRef]): Unit = {}

  // Override this to send to InternalAkkaHttpRouteContainer or External...Container if desired
  def addRoute(r: Route): Unit = WebsocketAkkaHttpRouteContainer.addRoute(r)

  // End standard overrides
  // Props for output SocketActor
  def callbackActor(bean: MapBean): Props = Props(new SocketActor(bean))

  // This the the main method to route WS messages
  protected def webSocketService(bean: MapBean, encodings: List[HttpEncodingRange]): Flow[Message, Message, Any] = {
    val sActor = context.system.actorOf(callbackActor(bean))
    val sink =
      Flow[Message].map {
        case tm: TextMessage if tm.getStrictText == "keepalive" =>
          Nil
        case tm: TextMessage ⇒
          (tm, bean)
        case bm: BinaryMessage =>
          if (isStreamingText) {
            val stringStream = bm.dataStream.map[String](sd => sd.utf8String)
            (TextMessage(stringStream), bean)
          } else {
            (TextMessage(bm.getStrictData.utf8String), bean)
          }
        case m =>
          log.warn("Unknown message: " + m)
          Nil
      }.to(Sink.actorRef(sActor, CloseSocket()))

    val compression = supported.find(enc => encodings.exists(_.matches(enc)))
    val source: Source[Message, Any] =
      Source.actorRef[Message](10, OverflowStrategy.dropHead).mapMaterializedValue { outgoingActor =>
        sActor ! Connect(outgoingActor, isStreamingText)
      } map {
        case tx: TextMessage if compression.nonEmpty => compress(tx.getStrictText, compression)
        // TODO Add support for binary message compression if anyone ends up wanting it
        case mess => mess
      }
    val livingSource = if (keepAliveOn) source.keepAlive(settings.ws.keepAliveFrequency, () => TextMessage("heartbeat"))
      else source

    Flow.fromSinkAndSourceCoupled(sink, livingSource)
  }

  // Route used to send along our websocket messages and make the initial handshake
  protected def commandOuterDirective: Route = check { bean =>
    extractRequest { req =>
      // Query params that can be marshalled to a case class via httpParams
      val reqHeaders = req.headers.map(h => h.name.toLowerCase -> h.value).toMap
      bean.addValue("RequestHeaders", reqHeaders)
        req.header[AcceptEncoding] match {
          case Some(encoding) =>
            supported.find(enc => encoding.getEncodings.toList.exists(_.matches(enc))) match {
              case Some(compression) => respondWithHeader(ContentEncoding.create(compression)) {
                handleWebSocketMessages(webSocketService(outputBean, encoding.getEncodings.toList))
              }
              case None => handleWebSocketMessages(webSocketService(outputBean, encoding.getEncodings.toList))
            }
          case None =>
            handleWebSocketMessages(webSocketService(outputBean, List()))
        }
    }
  }

  override protected def getHealthChildren = List()

  // Directive to check out path for matches and extract params
  protected def check(path: String): Directive1[MapBean] = {
    var bean: Option[MapBean] = None
    val filt = extractUri.filter({ uri =>
      bean = matchPath(path.toLowerCase, uri.path.toString().toLowerCase)
      bean.isDefined
    })
    filt flatMap { _ =>
      provide(bean.get)
    }
  }

  // Do the encoding of the results
  protected def compress(text: String, compression: Option[HttpEncoding]): Message = {
    val bos = new ByteArrayOutputStream(text.length)
    val zipper = if (compression.get.value == HttpEncodings.gzip.value) new GZIPOutputStream(bos)
    else if (compression.get.value == HttpEncodings.deflate.value) new DeflaterOutputStream(bos)
    else new PrintStream(bos)

    try {
      zipper.write(text.getBytes)
      zipper.close()
      BinaryMessage(ByteString(bos.toByteArray))
    } finally {
      bos.close()
    }
  }

  // Extractor to make sure our path matches, and extract URI params
  object PathCheck {
    def unapply(test: Uri): Option[MapBean] = {
      matchPath(test.path.toString(), path)
    }
  }

  case class CloseSocket() // We get this when websocket closes
  case class Connect(actorRef: ActorRef, isStreamingText: Boolean) // Initial connection

  // Actor that exists per each open websocket and closes when the WS closes, also routes back return messages
  class SocketActor(bean: MapBean) extends Actor {
    private[websocket] var callbactor: Option[ActorRef] = None

    override def postStop() = {
      openSocketGauge.incr(-1)
      onWebsocketClose(bean, callbactor)
      super.postStop()
    }

    override def preStart() = {
      openSocketGauge.incr(1)
      super.preStart()
    }

    def receive: Receive = starting

    def starting: Receive = {
      case Connect(actor, isStreamingText) =>
        callbactor = Some(actor) // Set callback actor
        context become open(isStreamingText)
        context.watch(actor)
      case _: CloseSocket =>
        context.stop(self)
    }

    // When becoming this, callbactor should already be set
    def open(isStreamingText: Boolean): Receive = {
      case tmb: (TextMessage, MapBean) if isStreamingText =>
        val returnText = handleTextStream(tmb._1.textStream, tmb._2, callbactor.get)
        returnText.foreach(tx => callbactor.get ! tx)
      case tmb: (TextMessage, MapBean) =>
        val returnText = handleText(tmb._1.getStrictText, tmb._2, callbactor.get)
        returnText.foreach(tx => callbactor.get ! tx)
      case Terminated(actor) =>
        if (callbactor.exists(_.path.equals(actor.path))) {
          log.debug(s"Linked callback actor terminated ${actor.path.name}, closing down websocket")
          context.stop(self)
        }
      case _: CloseSocket =>
        context.stop(self)
      case _ => // Mainly for eating the keep alive
    }
  }

  log.info(s"Adding Websocket on path $path to routes")

  /**
   * Checks the match between a test path and url path
   *
   * @param commandPath The test path of the command, like /test/$var1/ping
   * @param requestPath The uri requested, like /test/1/ping
   * @return Will return a command bean if matched, None if not and in the command bean the
   *         key var1 will equal 1 as per the example above
   */
  def matchPath(commandPath:String, requestPath:String) : Option[MapBean] = {

    import com.webtrends.harness.utils.StringPathUtil._

    val bean = MapBean(mutable.Map.empty[String, Any])
    val urlPath = requestPath.splitPath()

    val matched = urlPath.corresponds(commandPath.splitPath()) {
      // Convert the segment into an Integer if possible, otherwise leave it as a String
      case (uri, test) if test.head == '$' =>
        val key = test.substring(1)
        Try(uri.toInt) match {
          case Success(v) => bean.addValue(key, v.asInstanceOf[Integer])
          case Failure(_) => bean.addValue(key, uri)
        }
        true

      // Treat the value as a string
      case (uri, test) if test.head == '%' =>
        bean.addValue(test.drop(1), uri)
        true

      // Only match if the value is an INT
      case (uri, test) if test.head == '#' =>
        Try(uri.toInt) match {
          case Success(v) =>
            bean.addValue(test.drop(1), v.asInstanceOf[Integer])
            true
          case Failure(_) =>
            false
        }

      case (uri, test) =>
        test.toLowerCase.split('|').contains(uri.toLowerCase)
    }

    if (matched) Some(bean)
    else None
  }
}