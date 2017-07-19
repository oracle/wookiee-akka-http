package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.{BaseCommand, CommandBean}

import scala.concurrent.Future

case class GetRoute()

trait AkkaHttpWebsocket extends BaseCommand with HActor {
  implicit val materializer = ActorMaterializer(None, None)(context)

  // Can be implemented if text is desired to be streamed (must override isStreamingText = true)
  def handleTextStream(ts: Source[String, _]): TextMessage = ???
  // Main handler for incoming messages
  def handleText(text: String): TextMessage
  // Main handler for incoming binary
  def handleBinary(bm: BinaryMessage): BinaryMessage = {
    bm.dataStream.runWith(Sink.ignore)
    bm
  }


  // The Greeter WebSocket Service expects a "name" per message and
  // returns a greeting message for that name
  val webSocketService: Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      // we match but don't actually consume the text message here,
      // rather we simply stream it back as the tail of the response
      // this means we might start sending the response even before the
      // end of the incoming message has been received
      case tm: TextMessage ⇒
        (if (isStreamingText)
          handleTextStream(tm.textStream)
        else
          handleText(tm.getStrictText)):: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        handleBinary(bm)
        Nil
    }

  val webSocketRoute: Route = p(path) {
    handleWebSocketMessages(webSocketService)
  }

  val requestHandler: HttpRequest ⇒ HttpResponse = {
    case req @ HttpRequest(HttpMethods.GET, Uri.Path(path), _, _, _) ⇒
      req.header[UpgradeToWebSocket] match {
        case Some(upgrade) ⇒ upgrade.handleMessages(webSocketService)
        case None          ⇒ HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case _: HttpRequest ⇒ HttpResponse(404, entity = "Unknown resource!")
  }

  override def receive = super.receive orElse {
    case GetRoute() => sender() ! webSocketRoute
  }

  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = {
    Future.successful(AkkaHttpCommandResponse(None))
  }

  def isStreamingText: Boolean = false
}
