package com.webtrends.harness.component.akkahttp.websocket

import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, WebsocketAkkaHttpRouteContainer}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class WebsocketTest extends AnyWordSpecLike with Matchers with ScalatestRouteTest {
  case class AuthHolder(value: String)
  case class ParamHolder(p1: String, q1: String)
  case class Input(value: String)
  case class Output(value: String)

  "Websockets" should {
    implicit val ec: ExecutionContext = system.dispatcher

    def routes: Route = WebsocketAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
    var closed: Boolean = false

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, AuthHolder](
      "basic",
      { _ => Future.successful(AuthHolder("none")) },
      { (_, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s.getStrictText)) },
      { input =>
        println(s"got input $input")
        Future.successful(Output(input.value + "-output")) },
      { resp => TextMessage(resp.value) },
      { _: AuthHolder =>
        println("Called onClose")
        closed = true
      },
      { _ => {
        case err: Throwable =>
          err.printStackTrace()
          failWith(err)
      }}
    )

    "basic websocket support" in {
      val wsClient = WSProbe()

      WS("/basic", wsClient.flow) ~> routes ~>
        check {
          wsClient.sendMessage("abcdef")
          wsClient.expectMessage("abcdef-output")

          wsClient.sendCompletion()
          wsClient.expectCompletion()

          closed mustEqual true
        }
    }

    AkkaHttpEndpointRegistration.addAkkaWebsocketEndpoint[Input, Output, ParamHolder](
      "param/$p1",
      { req =>
        Future.successful(ParamHolder(req.segments.head, req.queryParams("q1"))) },
      { (params: ParamHolder, msg: TextMessage) => msg.toStrict(5.seconds).map(s => Input(s"${params.p1}-${params.q1}-${s.getStrictText}")) },
      { input =>
        println(s"got input $input")
        Future.successful(Output(input.value + "-output")) },
      { resp => TextMessage(resp.value) },
      { _: ParamHolder => println("Called onClose") },
      { _ => {
        case err: Throwable =>
          err.printStackTrace()
          failWith(err)
      }}
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
  }
}
