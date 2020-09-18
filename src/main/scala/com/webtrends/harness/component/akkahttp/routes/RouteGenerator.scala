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

package com.webtrends.harness.component.akkahttp.routes

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.Locale
import java.util.Locale.LanguageRange
import java.util.zip.{DeflaterOutputStream, GZIPOutputStream}

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.actor.TypedActor.context
import akka.http.scaladsl.model.headers.{HttpEncoding, HttpEncodingRange, HttpEncodings, Origin, `Accept-Encoding`, `Access-Control-Allow-Credentials`, `Access-Control-Allow-Origin`, `Access-Control-Expose-Headers`, `Content-Encoding`}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.{ByteString, Timeout}
import ch.megard.akka.http.cors.scaladsl.{CorsDirectives, CorsRejection}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.app.Harness.log
import com.webtrends.harness.command.ExecuteCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpSettings
import com.webtrends.harness.component.akkahttp.logging.AccessLog
import com.webtrends.harness.component.metrics.TimerStopwatch
import com.webtrends.harness.component.metrics.metrictype.Counter
import com.webtrends.harness.logging.Logger

import scala.collection.JavaConverters._
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}


trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

case class AkkaHttpRequest(
                            path: String,
                          // List, not Seq as Seq is not <: Product and route params are common command inputs
                            segments: List[String],
                            method: HttpMethod,
                            protocol: HttpProtocol,
                            requestHeaders: Map[String, String],
                            queryParams: Map[String, String],
                            time: Long,
                            locales: List[Locale],
                            requestBody: Option[RequestEntity] = None
                          )

case class WebsocketRequest(
                             path: String,
                             method: HttpMethod,
                             protocol: HttpProtocol,
                             requestHeaders: Map[String, String],
                             requestBody: Option[RequestEntity] = None
                          )

trait StreamRequest

case class StreamRequest(
                          ts: Source[String, _],
                          req: StreamRequest,
                          callback: ActorRef
                        ) extends StreamRequest

case class TextRequest(
                          text: String,
                          req: StreamRequest,
                          callback: ActorRef
                        ) extends StreamRequest

object RouteGenerator {
  val supported = List(HttpEncodings.gzip, HttpEncodings.deflate)
  val settings = AkkaHttpSettings(ConfigFactory.defaultApplication())
//  val openSocketGauge = Counter(s"${AkkaHttpBase.AHMetricsPrefix}.websocket.${path.replaceAll("/", "-")}.open-count")

  def makeHttpRoute[T <: Product : ClassTag, V](path: String,
                                                method: HttpMethod,
                                                commandRef: ActorRef,
                                                requestHandler: AkkaHttpRequest => Future[T],
                                                responseHandler: V => Route,
                                                errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                responseTimeout: FiniteDuration,
                                                timeoutHandler: HttpRequest => HttpResponse,
                                                accessLogIdGetter: Option[AkkaHttpRequest => String] = Some(_ => "-"),
                                                defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader],
                                                corsSettings: Option[CorsSettings] = None,
                                                timerName: Option[String] = None)
                                               (implicit ec: ExecutionContext, log: Logger): Route = {

    val httpPath = parseRouteSegments(path)
    ignoreTrailingSlash {
      httpPath { segments: AkkaHttpPathSegments =>
        respondWithHeaders(defaultHeaders: _*) {
          parameterMap { paramMap: Map[String, String] =>
            val timer = timerName.map(TimerStopwatch(_))
            extractRequest { request =>
              val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
              val httpEntity = getPayload(method, request)
              val locales = requestLocales(reqHeaders)
              val reqWrapper = AkkaHttpRequest(request.uri.path.toString, paramHoldersToList(segments), request.method, request.protocol,
                reqHeaders, paramMap, System.currentTimeMillis(), locales, httpEntity)
              corsSupport(method, corsSettings, reqWrapper, accessLogIdGetter) {
                httpMethod(method) {
                  // Timeout behavior requires that CORS has already been enforced
                  withRequestTimeout(responseTimeout, req => timeoutCors(req, timeoutHandler(req), corsSettings)) {
                    // http request handlers should be built with authorization in mind.
                    implicit val akkaTimeout: Timeout = responseTimeout
                    onComplete((for {
                      requestObjs <- requestHandler(reqWrapper)
                      commandResult <- commandRef ? ExecuteCommand("", requestObjs, responseTimeout)
                    } yield responseHandler(commandResult.asInstanceOf[V])).recover(errorHandler(reqWrapper))) {
                      case Success(route: Route) =>
                        mapRouteResult {
                          case Complete(response) =>
                            accessLogIdGetter.foreach(g => AccessLog.logAccess(reqWrapper, g(reqWrapper), response.status))
                            timer.foreach(finishTimer(_, response.status.intValue))
                            Complete(response)
                          case Rejected(rejections) =>
                            // TODO: Current expectation is that user's errorHandler should already handle rejections before this point
                            ???
                        }(route)
                      case Failure(ex: Throwable) =>
                        val firstClass = ex.getStackTrace.headOption.map(_.getClassName)
                          .getOrElse(ex.getClass.getSimpleName)
                        log.warn(s"Unhandled Error [$firstClass - '${ex.getMessage}'], update rejection handlers for path: ${path}", ex)
                        accessLogIdGetter.foreach(g => AccessLog.logAccess(reqWrapper, g(reqWrapper), StatusCodes.InternalServerError))
                        timer.foreach(finishTimer(_, StatusCodes.InternalServerError.intValue))
                        complete(StatusCodes.InternalServerError, "There was an internal server error.")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  def makeWebsocketRoute[T <: Product : ClassTag, V](path: String,
                                                method: HttpMethod,
                                                commandRef: ActorRef,
                                                requestHandler: StreamRequest => TextMessage,
                                                responseHandler: V => Route,
                                                errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                responseTimeout: FiniteDuration,
                                                timeoutHandler: HttpRequest => HttpResponse,
                                                accessLogIdGetter: Option[AkkaHttpRequest => String] = Some(_ => "-"),
                                                defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader],
                                                corsSettings: Option[CorsSettings] = None,
                                                timerName: Option[String] = None)
                                               (implicit ec: ExecutionContext, log: Logger): Route = {

    extractRequest { req =>
      // Query params that can be marshalled to a case class via httpParams
      val reqHeaders = req.headers.map(h => h.name.toLowerCase -> h.value).toMap

      // Override if you want the TextMessage content to be left as a stream
      def isStreamingText: Boolean = false
      val reqWeb = WebsocketRequest(req.uri.path.toString(), req.method, req.protocol, reqHeaders, None)

//      beanDirective(bean, path, method) { outputBean =>
        req.header[`Accept-Encoding`] match {
          case Some(encoding) =>
            supported.find(enc => encoding.getEncodings.toList.exists(_.matches(enc))) match {
              case Some(compression) => respondWithHeader(`Content-Encoding`(compression)) {
                handleWebSocketMessages(webSocketService(reqWeb, encoding.encodings.toList, requestHandler, isStreamingText))
              }
              case None => handleWebSocketMessages(webSocketService(reqWeb, encoding.encodings.toList, requestHandler))
            }
          case None =>
            handleWebSocketMessages(webSocketService(reqWeb, List(), requestHandler))
        }
//      }
    }



    // This the the main method to route WS messages
    def webSocketService(req: WebsocketRequest, encodings: List[HttpEncodingRange],
                         requestHandler: StreamRequest => TextMessage,
                         isStreamingText: Boolean = false): Flow[Message, Message, Any] = {
      val sActor = context.system.actorOf(Props(new SocketActor(req, requestHandler)))
      val sink =
        Flow[Message].map {
          case tm: TextMessage if tm.getStrictText == "keepalive" =>
            Nil
          case tm: TextMessage ⇒
            (tm, req)
          case bm: BinaryMessage =>
            if (isStreamingText) {
              val stringStream = bm.dataStream.map[String](sd => sd.utf8String)
              (TextMessage(stringStream), req)
            } else {
              (TextMessage(bm.getStrictData.utf8String), req)
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
      val livingSource = if (settings.ws.keepAliveOn) source.keepAlive(settings.ws.keepAliveFrequency, () => TextMessage("heartbeat"))
      else source

      Flow.fromSinkAndSourceCoupled(sink, livingSource)
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

  case class CloseSocket() // We get this when websocket closes
  case class Connect(actorRef: ActorRef, isStreamingText: Boolean) // Initial connection

  // Actor that exists per each open websocket and closes when the WS closes, also routes back return messages
  class SocketActor(req: WebsocketRequest, requestHandler: StreamRequest => TextMessage) extends Actor {
    private[websocket] var callbactor: Option[ActorRef] = None

    override def postStop() = {
//      openSocketGauge.incr(-1)
//      onWebsocketClose(bean, callbactor)
      super.postStop()
    }

    override def preStart() = {
//      openSocketGauge.incr(1)
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
      case tmb: (TextMessage, StreamRequest) if isStreamingText =>
        val returnText = requestHandler(StreamRequest(tmb._1.textStream, tmb._2, callbactor.get))
        callbactor.get ! returnText
      case tmb: (TextMessage, StreamRequest) =>
        val returnText = requestHandler(TextRequest(tmb._1.getStrictText, tmb._2, callbactor.get))
        callbactor.get ! returnText
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

  // At this point in the route, the CORS directive has already been applied, meaning that pre-flight requests have
  // already been handled and any invalid requests have  been rejected.
  // Rather than re-apply the CORS logic here (which isn't accessible) we simply echo back the request origin to allow
  // clients to handle the timeout response correctly
  private def timeoutCors(request: HttpRequest, response: HttpResponse, cors: Option[CorsSettings]): HttpResponse = {
    response.copy(
      headers = response.headers ++ ((cors, request.header[Origin]) match {
        case (Some(c), Some(o)) =>
          List(
            Some(`Access-Control-Allow-Origin`(o.origins.head)),
            if (c.allowCredentials) Some(`Access-Control-Allow-Credentials`(true)) else None,
            if (c.exposedHeaders.nonEmpty) Some(`Access-Control-Expose-Headers`(c.exposedHeaders)) else None
          ).flatten
        case _ =>
          List()
      })
    )
  }

  // TODO: #129
  // Use these to generically extract values from a query string
  private case class Holder1(_1: String) extends Product1[String] with AkkaHttpPathSegments
  private case class Holder2(_1: String, _2: String) extends Product2[String, String] with AkkaHttpPathSegments
  private case class Holder3(_1: String, _2: String, _3: String) extends Product3[String, String, String] with AkkaHttpPathSegments
  private case class Holder4(_1: String, _2: String, _3: String, _4: String)
    extends Product4[String, String, String, String] with AkkaHttpPathSegments
  private case class Holder5(_1: String, _2: String, _3: String, _4: String, _5: String)
    extends Product5[String, String, String, String, String] with AkkaHttpPathSegments
  private case class Holder6(_1: String, _2: String, _3: String, _4: String, _5: String, _6: String)
    extends Product6[String, String, String, String, String, String] with AkkaHttpPathSegments

  // TODO: #129
  // Don't expose this, just use the paramHoldersToList to change it to something actually useful.
  protected[routes] def parseRouteSegments(path: String)(implicit log: Logger): Directive1[AkkaHttpPathSegments] = {
    val segs = path.split("/").filter(_.nonEmpty).toSeq
    var segCount = 0
    try {
      val dir = segs.tail.foldLeft(segs.head.asInstanceOf[Any]) { (x, y) =>
        y match {
          case s1: String if s1.startsWith("$") =>
            segCount += 1
            (x match {
              case pStr: String => if (pStr.startsWith("$")) {
                segCount += 1
                Segment / Segment
              } else pStr / Segment
              case pMatch: PathMatcher[Unit] if segCount == 1 => pMatch / Segment
              case pMatch: PathMatcher[Tuple1[String]] if segCount == 2 => pMatch / Segment
              case pMatch: PathMatcher[(String, String)] if segCount == 3 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String)] if segCount == 4 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String, String)] if segCount == 5 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String, String, String)] if segCount == 6 => pMatch / Segment

            }).asInstanceOf[PathMatcher[_]]
          case s1: String =>
            (x match {
              case pStr: String => if (pStr.startsWith("$")) {
                segCount += 1
                Segment / s1
              } else pStr / s1
              case pMatch: PathMatcher[_] => pMatch / s1
            }).asInstanceOf[PathMatcher[_]]
        }
      }

      // Create holders for any arguments on the query path
      segCount match {
        case 0 if segs.size == 1 => p(path) & provide(new AkkaHttpPathSegments {})
        case 0 => p(dir.asInstanceOf[PathMatcher[Unit]]) & provide(new AkkaHttpPathSegments {})
        case 1 => p(dir.asInstanceOf[PathMatcher[Tuple1[String]]]).as(Holder1)
        case 2 => p(dir.asInstanceOf[PathMatcher[(String, String)]]).as(Holder2)
        case 3 => p(dir.asInstanceOf[PathMatcher[(String, String, String)]]).as(Holder3)
        case 4 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String)]]).as(Holder4)
        case 5 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String)]]).as(Holder5)
        case 6 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String, String)]]).as(Holder6)
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Error adding path ${path}", ex)
        throw ex
    }
  }

  // TODO: #129
  private def paramHoldersToList(segments: AkkaHttpPathSegments): List[String] =
    segments match {
      case Holder1(a) => List(a)
      case Holder2(a, b) => List(a, b)
      case Holder3(a, b, c) => List(a, b, c)
      case Holder4(a, b, c, d) => List(a, b, c, d)
      case Holder5(a, b, c, d, e) => List(a, b, c, d, e)
      case Holder6(a, b, c, d, e, f) => List(a, b, c, d, e, f)
      case _ => List()
    }

  def getPayload(method: HttpMethod, request: HttpRequest): Option[RequestEntity] = method match {
    case HttpMethods.PUT | HttpMethods.POST | HttpMethods.PATCH => Some(request.entity)
    case _ => None
  }

  private def corsSupport(method: HttpMethod,
                          corsSettings: Option[CorsSettings],
                          request: AkkaHttpRequest,
                          accessLogIdGetter: Option[AkkaHttpRequest => String]): Directive0 =
    corsSettings match {
      case Some(cors) => handleRejections(corsRejectionHandler(request, accessLogIdGetter)) &
        CorsDirectives.cors(cors.withAllowedMethods((cors.allowedMethods ++ immutable.Seq(method)).distinct))
      case None => pass
    }

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
    case HttpMethods.PATCH => patch
  }

  def requestLocales(headers: Map[String, String]): List[Locale] =
    headers.get("accept-language") match {
      case Some(localeString) if localeString.nonEmpty => LanguageRange.parse(localeString).asScala
        .map(language => Locale.forLanguageTag(language.getRange)).toList
      case _ => Nil
    }

  private def corsRejectionHandler(request: AkkaHttpRequest, accessLogIdGetter: Option[AkkaHttpRequest => String]): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handleAll[CorsRejection] { rejections =>
        val causes = rejections.map(_.cause.description).mkString(", ")
        accessLogIdGetter.foreach(g => AccessLog.logAccess(request, g(request), StatusCodes.Forbidden))
        complete((StatusCodes.Forbidden, s"CORS: $causes"))
      }
      .result()

  private def finishTimer(timer: TimerStopwatch, statusCode: Int): Unit =
    statusCode match {
      case n if n >= 200 && n < 400 => timer.success
      case n if n >= 400 && n < 500 => timer.failure("request")
      case _ => timer.failure("server")
    }

}
