package com.webtrends.harness.component.akkahttp

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.util.concurrent.TimeUnit
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.component.akkahttp.routes.{InternalAkkaHttpRouteContainer, WebsocketAkkaHttpRouteContainer}
import com.webtrends.harness.component.akkahttp.websocket.AkkaHttpWebsocket
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestWebsocket extends AkkaHttpWebsocket {
  override def path = "greeter/$var1"

  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    Some(TextMessage(s"Hello $text! var1: ${bean("var1")}"))
  }

  override def commandName = "TestWebsocket"
}

class TestWebsocketExtra extends AkkaHttpWebsocket {
  override def path = "greeter2/$var1"

  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    callback ! TextMessage(s"Hello $text! var1: ${bean("var1")}")
    None
  }

  override def commandName = "TestWebsocketExtra"
}

class TestWebsocketLong extends AkkaHttpWebsocket {
  override def path = "long/$var1/url/$var2/$var3/test"

  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    if (text == "empty") None
    else Some(TextMessage(Source.single(s"$text! ${bean("var1")} ${bean("var2")} ${bean("var3")}")))
  }

  override def commandName = "TestWebsocketLong"
}

class TestWebsocketInternal extends AkkaHttpWebsocket {
  override def path = "internal/$var1"

  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    Some(TextMessage(s"Hello $text! var1: ${bean("var1")}"))
  }

  override def commandName = "TestWebsocket"

  override def addRoute(r: Route) = InternalAkkaHttpRouteContainer.addRoute(r)
}


class TestWebsocketStream extends AkkaHttpWebsocket {
  override def path = "stream"

  override def handleTextStream(text: Source[String, _], bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    val ret = text.runWith(Sink.head[String])
    Some(TextMessage(Await.result(ret, checkTimeout.duration)))
  }

  // We have to override this since it's the standard case, but we'll never hit it if we're using streaming
  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = None

  override def isStreamingText = true
  override def commandName = "TestWebsocketStream"
}

class TestWebsocketClose extends AkkaHttpWebsocket {
  override implicit def materializer = ActorMaterializer(None, None)(context)
  override def path = "close"

  override def handleText(text: String, bean: CommandBean, callback: ActorRef): Option[TextMessage] = {
    Some(TextMessage("Close Actor"))
  }

  override def onWebsocketClose(bean: CommandBean, callback: Option[ActorRef]) = {
    ClosedObject.closed = true
  }
  override def commandName = "TestWebsocketClose"
}

object ClosedObject {
  @volatile var closed = false
}

class AkkaHttpWebsocketTest extends WordSpecLike
  with ScalatestRouteTest
  with MustMatchers {
  // This should be all you need to get your routes for the WSProbe
  // when making tests for your websocket classes
  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  // Wait for the actor to be up or routes will be empty
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocket]).path).resolveOne(), Duration("5 seconds"))
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocketExtra]).path).resolveOne(), Duration("5 seconds"))
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocketLong]).path).resolveOne(), Duration("5 seconds"))
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocketInternal]).path).resolveOne(), Duration("5 seconds"))
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocketStream]).path).resolveOne(), Duration("5 seconds"))
  Await.result(system.actorSelection(system.actorOf(Props[TestWebsocketClose]).path).resolveOne(), Duration("5 seconds"))
  val routes = WebsocketAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
  val inRoutes = InternalAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
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
          wsClient.expectMessage("Hello abcdef! var1: friend")

          wsClient.sendMessage("John")
          wsClient.expectMessage("Hello John! var1: friend")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "be able to send back data with a TextMessage" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter2/friend", wsClient.flow) ~> routes ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Hello Peter! var1: friend")

          wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
          wsClient.expectMessage("Hello abcdef! var1: friend")

          wsClient.sendMessage("John")
          wsClient.expectMessage("Hello John! var1: friend")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "be able to gzip compress output" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter2/friend", wsClient.flow)
        .addHeader(`Accept-Encoding`(HttpEncodingRange(HttpEncodings.gzip))) ~> routes ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          val message = wsClient.expectMessage().asBinaryMessage.getStrictData
          val bais = new ByteArrayInputStream(message.toArray)
          val gzis = new GZIPInputStream(bais)
          val reader = new InputStreamReader(gzis)
          val in = new BufferedReader(reader)

          val decompressed = in.readLine()
          decompressed mustEqual "Hello Peter! var1: friend"

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "be able to deflate compress output" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter2/friend", wsClient.flow)
        .addHeader(`Accept-Encoding`(HttpEncodingRange(HttpEncodings.deflate))) ~> routes ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          val message = wsClient.expectMessage().asBinaryMessage.getStrictData
          val bais = new ByteArrayInputStream(message.toArray)
          val gzis = new InflaterInputStream(bais)
          val reader = new InputStreamReader(gzis)
          val in = new BufferedReader(reader)

          val decompressed = in.readLine()
          decompressed mustEqual "Hello Peter! var1: friend"

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "miss the websocket with wrong URI" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter/friend/wrong", wsClient.flow) ~> routes ~>
        check {
          try {
            isWebSocketUpgrade mustEqual true
            false mustEqual true
          } catch {
            case _: Throwable => // Expected to fail
          }
        }
    }

    "handle more than one url param" in {
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/long/one/url/two/three/test", wsClient.flow) ~> routes ~>
        check {
          isWebSocketUpgrade mustEqual true

          // Test None response
          wsClient.sendMessage("empty")

          wsClient.sendMessage("test")
          wsClient.expectMessage("test! one two three")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "miss websockets on internal server when hitting websocket server" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/internal/friend", wsClient.flow) ~> routes ~>
        check {
          try {
            isWebSocketUpgrade mustEqual true
            false mustEqual true
          } catch {
            case _: Throwable => // Expected to fail
          }
        }
    }

    "hit the websocket on an internal server when registered there" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/internal/friend", wsClient.flow) ~> inRoutes ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Hello Peter! var1: friend")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "handle streaming text input" in {
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/stream", wsClient.flow) ~> routes ~>
        check {
          isWebSocketUpgrade mustEqual true

          wsClient.sendMessage("test")
          wsClient.expectMessage("test")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "hit its closing method" in {
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/close", wsClient.flow) ~> routes ~>
        check {
          isWebSocketUpgrade mustEqual true

          wsClient.sendMessage("test")
          wsClient.expectMessage("Close Actor")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
          Thread.sleep(300)
          ClosedObject.closed mustEqual true
        }
    }
  }
}
