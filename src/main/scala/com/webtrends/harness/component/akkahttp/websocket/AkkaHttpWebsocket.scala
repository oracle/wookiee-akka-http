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
import com.webtrends.harness.command.{Command, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.RequestHeaders
import com.webtrends.harness.component.akkahttp.routes.WebsocketAkkaHttpRouteContainer
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse, AkkaHttpSettings}
import com.webtrends.harness.component.metrics.metrictype.Counter

import scala.collection.JavaConversions._
import scala.concurrent.Future


trait AkkaHttpWebsocket extends Command with HActor with AkkaHttpBase {
  val supported = List(HttpEncodings.gzip, HttpEncodings.deflate)
  val settings = AkkaHttpSettings(config)
  val openSocketGauge = Counter(s"${AkkaHttpBase.AHMetricsPrefix}.websocket.${path.replaceAll("/", "-")}.open-count")

  implicit def materializer = ActorMaterializer(None, None)(context)
  // Standard overrides
  // Can be implemented if text is desired to be streamed (must override isStreamingText = true)
  def handleTextStream(ts: Source[String, _], bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    throw new NotImplementedError("Must override isStreamingText and handleTextStream to handle streaming text.")
  }

  // Main handler for incoming messages
  def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage]

  // Override if you want the TextMessage content to be left as a stream
  def isStreamingText: Boolean = false

  // Should we keep this websocket alive with heartbeat messages, will cause us to send a TextMessage("heartbeat")
  // back to the client every 30 seconds, override with true if you want just this websocket to do it
  def keepAliveOn: Boolean = settings.ws.keepAliveOn

  // Override for websocket closure code, callback will be None if we aren't connected yet
  def onWebsocketClose(bean: CommandBean, callback: Option[ActorRef]): Unit = {}

  // Override this to send to InternalAkkaHttpRouteContainer or External...Container if desired
  override def addRoute(r: Route): Unit = WebsocketAkkaHttpRouteContainer.addRoute(r)

  // End standard overrides
  // Props for output SocketActor
  def callbackActor(bean: CommandBean): Props = Props(new SocketActor(bean))

  // This the the main method to route WS messages
  protected def webSocketService(bean: CommandBean, encodings: List[HttpEncodingRange]): Flow[Message, Message, Any] = {
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
      Source.actorRef[Message](50, OverflowStrategy.backpressure).mapMaterializedValue { outgoingActor =>
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
  override protected def commandOuterDirective: Route = check { bean =>
    extractRequest { req =>
      // Query params that can be marshalled to a case class via httpParams
      val reqHeaders = req.headers.map(h => h.name.toLowerCase -> h.value).toMap
      bean.addValue(RequestHeaders, reqHeaders)
      beanDirective(bean, path, method) { outputBean =>
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
  }

  // Overriding this so that extensions don't need to
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = {
    Future.successful(AkkaHttpCommandResponse(None))
  }

  override protected def getHealthChildren = List()

  // Directive to check out path for matches and extract params
  protected def check: Directive1[CommandBean] = {
    var bean: Option[CommandBean] = None
    val filt = extractUri.filter({ uri =>
      bean = Command.matchPath(path.toLowerCase, uri.path.toString().toLowerCase)
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
    def unapply(test: Uri): Option[CommandBean] = {
      Command.matchPath(test.path.toString(), path)
    }
  }

  case class CloseSocket() // We get this when websocket closes
  case class Connect(actorRef: ActorRef, isStreamingText: Boolean) // Initial connection

  // Actor that exists per each open websocket and closes when the WS closes, also routes back return messages
  class SocketActor(bean: CommandBean) extends Actor {
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
      case tmb: (TextMessage, CommandBean) if isStreamingText =>
        val returnText = handleTextStream(tmb._1.textStream, tmb._2, callbactor.get)
        returnText.foreach(tx => callbactor.get ! tx)
      case tmb: (TextMessage, CommandBean) =>
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
}
