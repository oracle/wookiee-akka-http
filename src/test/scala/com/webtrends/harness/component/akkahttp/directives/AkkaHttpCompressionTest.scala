package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.HttpEncodings._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpCommandResponse
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpGet
import com.webtrends.harness.component.akkahttp.util.TestBaseCommand
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Inside, MustMatchers, WordSpec}

import scala.collection._
import scala.concurrent.Future

class AkkaHttpCompressionTest extends WordSpec
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest
  with Inside {

  "Commands" should {

    "have no compression by default" in {

      var routes = Set.empty[Route]
      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(Some("test".asInstanceOf[T])))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Get("/test") ~> `Accept-Encoding`(gzip) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        headers must (not contain `Content-Encoding`(gzip))
      }

    }

    "support gzip, deflate, and identity" in {

      var routes = Set.empty[Route]

      new AkkaHttpGet with AkkaHttpCompression with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(Some("test".asInstanceOf[T])))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Get("/test") ~> `Accept-Encoding`(gzip) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        headers must contain(`Content-Encoding`(gzip))
      }

      Get("/test") ~> `Accept-Encoding`(deflate) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        headers must contain(`Content-Encoding`(deflate))
      }

      Get("/test") ~> `Accept-Encoding`(identity) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        headers must (not contain `Content-Encoding`)
      }
    }
  }

}
