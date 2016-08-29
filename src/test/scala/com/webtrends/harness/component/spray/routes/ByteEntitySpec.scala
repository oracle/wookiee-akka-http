package com.webtrends.harness.component.spray.routes

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import com.webtrends.harness.component.spray.route._
import org.specs2.mutable.SpecificationWithJUnit
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.Specs2RouteTest

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

case class Response(bytesAsString: String, contentType: String)

class ByteEntityTestCommand() extends Command with SprayPostBytes with SprayPutBytes {
  implicit val executionContext = context.dispatcher
  override def commandName = "ByteEntityTest"
  override def path = s"/$commandName"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = Future {
    val dataAsString = new String(bean.get.getValue[Array[Byte]](CommandBean.KeyEntity).get, "utf-8")
    val contentType = bean.get.getValue[Option[String]](SprayRoutes.KeyEntityType).get.getOrElse("")
    new CommandResponse(Some(Response(dataAsString, contentType).asInstanceOf[T]))
  }

}

class ByteEntitySpec extends SpecificationWithJUnit
  with Directives
  with Specs2RouteTest
  with HttpService {

  implicit def default = RouteTestTimeout(FiniteDuration(60, TimeUnit.SECONDS))
  def actorRefFactory = system

  val testCommandRef = system.actorOf(Props(new ByteEntityTestCommand()))

  "SprayPostBytes " should {
    "Add Content-Type and raw byte array to Command Bean" in {

      HttpRequest(
        HttpMethods.POST,
        "/ByteEntityTest",
        List(HttpHeaders.`Content-Type`(ContentTypes.`application/json`)),
        Some(HttpEntity("TestString".getBytes("utf-8")))
      ) ~>
        RouteManager.getRoute("ByteEntityTest_post").get ~> check {
        responseAs[String] == """{"bytesAsString":"TestString","contentType":"application/json; charset=UTF-8"}""" && handled must beTrue
      }
    }

    "not require a content-type" in {

      HttpRequest(
        HttpMethods.POST,
        "/ByteEntityTest",
        List.empty[HttpHeader],
        Some(HttpEntity("TestString".getBytes("utf-8")))
      ) ~>
        RouteManager.getRoute("ByteEntityTest_post").get ~> check {
        responseAs[String] == """{"bytesAsString":"TestString","contentType":""}""" && handled must beTrue
      }
    }
  }

  "SprayPutBytes " should {
    "Add Content-Type and raw byte array to Command Bean" in {

      HttpRequest(
        HttpMethods.PUT,
        "/ByteEntityTest",
        List(HttpHeaders.`Content-Type`(ContentTypes.`application/json`)),
        Some(HttpEntity("TestString".getBytes("utf-8")))
      ) ~>
        RouteManager.getRoute("ByteEntityTest_put").get ~> check {
        responseAs[String] == """{"bytesAsString":"TestString","contentType":"application/json; charset=UTF-8"}""" && handled must beTrue
      }
    }

    "not require a content-type" in {

      HttpRequest(
        HttpMethods.PUT,
        "/ByteEntityTest",
        List.empty[HttpHeader],
        Some(HttpEntity("TestString".getBytes("utf-8")))
      ) ~>
        RouteManager.getRoute("ByteEntityTest_put").get ~> check {
        responseAs[String] == """{"bytesAsString":"TestString","contentType":""}""" && handled must beTrue
      }
    }
  }
}


