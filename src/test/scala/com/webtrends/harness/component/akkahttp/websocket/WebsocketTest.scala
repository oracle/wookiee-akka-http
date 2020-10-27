package com.webtrends.harness.component.akkahttp.websocket

import akka.http.WSWrapper
import akka.http.javadsl.model.headers.AcceptEncoding
import akka.http.javadsl.model.ws.BinaryMessage
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.WSProbe
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.scaladsl.{Compression, Sink, Source}
import com.webtrends.harness.component.akkahttp.routes.AkkaHttpEndpointRegistration.ErrorHolder
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, EndpointType, ExternalAkkaHttpRouteContainer}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class WebsocketTest extends WSWrapper {
  case class AuthHolder(value: String)
  case class ParamHolder(p1: String, q1: String)
  case class Input(value: String)
  case class Output(value: String)

  implicit val formats: DefaultFormats.type = DefaultFormats

  var errorThrown: Option[Throwable] = None
  val toOutput: (Input, WebsocketInterface[Input, Output, _]) => Unit = {
    (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
    println(s"got input $input")
    inter.reply(Output(input.value + "-output"))
  }
  val toText: Output => TextMessage.Strict = { resp: Output => TextMessage(resp.value) }

  "Websockets" should {
    implicit val ec: ExecutionContext = system.dispatcher

    def routes: Route = ExternalAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
    var closed: Boolean = false

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "basic",
      EndpointType.EXTERNAL,
      { _ => Future.successful(AuthHolder("none")) },
      { (_, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s.getStrictText)) },
      toOutput,
      toText,
      { _: AuthHolder =>
        println("Called onClose")
        closed = true
      }
    )

    "basic websocket support" in {
      val wsClient = WSProbe()

      WS("/basic", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")

          wsClient.sendMessage("abcdef2")
          wsClient.expectMessage("abcdef2-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()

          Thread.sleep(500L)
          closed mustEqual true
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, ParamHolder](
      "param/$p1",
      EndpointType.EXTERNAL,
      { req =>
        Future.successful(ParamHolder(req.segments.head, req.queryParams("q1"))) },
      { (params: ParamHolder, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s"${params.p1}-${params.q1}-${s.getStrictText}")) },
      toOutput,
      toText,
      { _: ParamHolder => println("Called onClose") }
    )

    "query parameter and segment support" in {
      val wsClient = WSProbe()

      WS("/param/pValue?q1=qValue", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("pValue-qValue-abcdef-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, ParamHolder](
      "error/auth",
      EndpointType.EXTERNAL,
      { _ => Future.failed(new IllegalAccessError("Not allowed buster!")) },
      { (_, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s.getStrictText)) },
      toOutput,
      toText,
      { _: ParamHolder => println("Called onClose") },
      { _: AkkaHttpRequest => {
        case err: IllegalAccessError =>
          println("Got ERROR:")
          err.printStackTrace()
          errorThrown = Some(err)
          complete(write(ErrorHolder(err.getMessage)))
      }}
    )

    "handles auth errors cleanly" in {
      val wsClient = WSProbe()

      val route = WS("/error/auth", wsClient.flow) ~> routes

      val resp = Await.result(route.entity.toStrict(5.seconds).map(_.getData().utf8String), 5.seconds)
      resp mustEqual """{"error":"Not allowed buster!"}"""
    }

    var resumeHit = false
    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "error/ws",
      EndpointType.EXTERNAL,
      { _ => Future.successful(AuthHolder("none")) },
      { (_, tm: TextMessage) =>
        println(s"Text: ${tm.getStrictText}")
        if (tm.getStrictText == "recover") throw new IllegalStateException("Will recover")
        else if (tm.getStrictText == "stop") throw new RuntimeException("Will stop the stream")
        else tm.toStrict(5.seconds).map(s => Input(s.getStrictText)) },
      toOutput,
      toText,
      { _: AuthHolder =>
        println("Called onClose")
        closed = true
      },
      wsErrorHandler = {
        case _: IllegalStateException =>
          println("Resume error hit")
          resumeHit = true
          Resume // Will skip the errant event
        case _: RuntimeException =>
          println("Stop error hit")
          Stop // Will close this stream
      }
    )

    "recover from errors during ws processing" in {
      val wsClient = WSProbe()
      closed = false

      WS("/error/ws", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("recover")
          wsClient.inProbe.ensureSubscription()

          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")
          resumeHit mustEqual true

          wsClient.sendMessage("stop")
          wsClient.expectCompletion()
          closed mustEqual true
        }
    }

    "compress data when requested via deflate" in {
      val wsClient = WSProbe()

      WS("/basic", wsClient.flow, Some(AcceptEncoding.create(HttpEncodings.deflate)), Nil) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")

          val bytes = wsClient.expectMessage().asInstanceOf[BinaryMessage].getStrictData
          val unzip = Source.single(bytes).via(Compression.inflate()).runWith(Sink.head)
          "abcdef-output" mustEqual new String(Await.result(unzip, 5.seconds).toArray)

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    "compress data when requested via gzip" in {
      val wsClient = WSProbe()

      WS("/basic", wsClient.flow, Some(AcceptEncoding.create(HttpEncodings.gzip)), Nil) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")

          val bytes = wsClient.expectMessage().asInstanceOf[BinaryMessage].getStrictData
          val unzip = Source.single(bytes).via(Compression.gunzip()).runWith(Sink.head)
          "abcdef-output" mustEqual new String(Await.result(unzip, 5.seconds).toArray)

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "multi",
      EndpointType.EXTERNAL,
      { _ => Future.successful(AuthHolder("none")) },
      { (_, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s.getStrictText)) },
      {
        (input: Input, inter: WebsocketInterface[Input, Output, _]) =>
          println(s"got multi input $input")
          inter.reply(Output(input.value + "-output1"))
          inter.reply(Output(input.value + "-output2"))
          inter.reply(Output(input.value + "-output3"))
      },
      toText,
      { _: AuthHolder =>
        println("Called onClose")
        closed = true
      }
    )

    "can send back more than one reply for a single input" in {
      val wsClient = WSProbe()

      WS("/multi", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output1")
          wsClient.expectMessage("abcdef-output2")
          wsClient.expectMessage("abcdef-output3")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }
  }
}