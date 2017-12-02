package com.webtrends.harness.component.akkahttp.websocket

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.{Command, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpCommandResponse
import com.webtrends.harness.component.akkahttp.routes.WebsocketAkkaHttpRouteContainer

import scala.concurrent.Future

trait AkkaHttpWebsocket extends Command with HActor {
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

  // Override for websocket closure code, callback will be None if we aren't connected yet
  def onWebsocketClose(bean: CommandBean, callback: Option[ActorRef]): Unit = {}

  // Override this to send to InternalAkkaHttpRouteContainer or External...Container if desired
  def addRoute(r: Route): Unit = WebsocketAkkaHttpRouteContainer.addRoute(r)

  // End standard overrides
  // Props for out SocketActor
  def callbackActor: Props = Props(new SocketActor)

  // This the the main method to route WS messages
  protected def webSocketService(bean: CommandBean): Flow[Message, Message, Any] = {
    val sActor = context.system.actorOf(callbackActor)
    val sink: Sink[Message, Any] =
      Flow[Message].map {
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
      }.to(Sink.actorRef(sActor, CloseSocket(bean)))

    val source: Source[Message, Any] =
      Source.actorRef[Message](10, OverflowStrategy.dropHead).mapMaterializedValue { outgoingActor =>
        sActor ! Connect(outgoingActor, isStreamingText)
      }

    Flow.fromSinkAndSourceCoupled(sink, source)
  }

  // Route used to send along our websocket messages and make the initial handshake
  protected def webSocketRoute: Route = check { bean =>
    extractRequest { req =>
      handleWebSocketMessages(webSocketService(bean))
    }
  }

  // Overriding this so that extensions don't need to
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = {
    Future.successful(AkkaHttpCommandResponse(None))
  }

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

  // Extractor to make sure our path matches, and extract URI params
  object PathCheck {
    def unapply(test: Uri): Option[CommandBean] = {
      Command.matchPath(test.path.toString(), path)
    }
  }

  case class CloseSocket(bean: CommandBean) // We get this when websocket closes
  case class Connect(actorRef: ActorRef, isStreamingText: Boolean) // Initial connection

  // Actor that exists per each open websocket and closes when the WS closes, also routes back return messages
  class SocketActor extends Actor {
    def receive: Receive = starting

    def starting: Receive = {
      case Connect(actor, isStreamingText) =>
        context become open(actor, isStreamingText)
      case CloseSocket(bean) =>
        onWebsocketClose(bean, None)
        context.stop(self)
    }

    def open(retActor: ActorRef, isStreamingText: Boolean): Receive = {
      case tmb: (TextMessage, CommandBean) if isStreamingText =>
        val returnText = handleTextStream(tmb._1.textStream, tmb._2, retActor)
        returnText.foreach(tx => retActor ! tx)
      case tmb: (TextMessage, CommandBean) =>
        val returnText = handleText(tmb._1.getStrictText, tmb._2, retActor)
        returnText.foreach(tx => retActor ! tx)
      case CloseSocket(bean) =>
        onWebsocketClose(bean, Some(retActor))
        context.stop(self)
    }
  }

  log.info(s"Adding Websocket on path $path to routes")
  addRoute(webSocketRoute)
}