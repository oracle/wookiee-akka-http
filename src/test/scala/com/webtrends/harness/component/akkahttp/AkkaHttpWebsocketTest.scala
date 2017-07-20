package com.webtrends.harness.component.akkahttp

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import com.webtrends.harness.command.CommandBean
import akka.http.scaladsl.server.Directives._
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestWebsocket extends AkkaHttpWebsocket {
  override def path = "greeter/$var1"

  override def handleText(text: String, bean: CommandBean): TextMessage = {
    TextMessage(Source.single(s"Hello $text! var1: ${bean("var1")}"))
  }
}

class AkkaHttpWebsocketTest extends WordSpecLike
  with ScalatestRouteTest
  with MustMatchers {
  // This should be all you need to get your routes for the WSProbe
  // when making tests for your websocket classes
  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val twsActor = system.actorOf(Props[TestWebsocket])
  // Wait for the actor to be up or routes will be empty
  Await.result(system.actorSelection(twsActor.path).resolveOne(), Duration("5 seconds"))
  val routes = ExternalAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
  // End of setup

  "AkkaHttpWebsocket" should {
    "be able to take websocket input" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter/friend", wsClient.flow) ~> routes ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Hello Peter! var1: friend")

          wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
          wsClient.expectMessage(ByteString("abcdef"))

          wsClient.sendMessage("John")
          wsClient.expectMessage("Hello John! var1: friend")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }
  }
}
