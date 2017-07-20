package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.{BaseCommand, Command, CommandBean}

import scala.concurrent.Future

trait AkkaHttpWebsocket extends BaseCommand with HActor {
  implicit val materializer = ActorMaterializer(None, None)(context)
  // Can be implemented if text is desired to be streamed (must override isStreamingText = true)
  def handleTextStream(ts: Source[String, _], bean: CommandBean): TextMessage = ???
  // Main handler for incoming messages
  def handleText(text: String, bean: CommandBean): TextMessage
  // Main handler for incoming binary
  def handleBinary(bm: BinaryMessage, bean: CommandBean): BinaryMessage = {
    bm.dataStream.runWith(Sink.ignore)
    bm
  }

  // Override if you want the TextMessage content to be left as a stream
  def isStreamingText: Boolean = false


  // This the the main method to routes WS messages
  def webSocketService(bean: CommandBean): Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage ⇒
        (if (isStreamingText)
          handleTextStream(tm.textStream, bean)
        else
          handleText(tm.getStrictText, bean)) :: Nil
      case bm: BinaryMessage =>
        handleBinary(bm, bean) :: Nil
    }

  // Route used to send along our websocket messages and make the initial handshake
  def webSocketRoute: Route = check { bean =>
    extractRequest { req =>
      handleWebSocketMessages(webSocketService(bean))
    }
  }

  // Overriding this so that extensions don't need to
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = {
    Future.successful(AkkaHttpCommandResponse(None))
  }

  // Directive to check out path for matches and extract params
  def check: Directive1[CommandBean] = {
    var bean: Option[CommandBean] = None
    val filt = extractUri.filter({ uri =>
      bean = Command.matchPath(path, uri.path.toString())
      bean.isDefined
    })
    filt flatMap { uri =>
      provide(bean.get)
    }
  }

  // Extractor to make sure our path matches, and extract URI params
  object PathCheck {
    def unapply(test: Uri): Option[CommandBean] = {
      Command.matchPath(test.path.toString(), path)
    }
  }

  ExternalAkkaHttpRouteContainer.addRoute(webSocketRoute)
}
