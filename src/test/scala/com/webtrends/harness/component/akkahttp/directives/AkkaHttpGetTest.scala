package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean, CommandResponse}
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpGet
import com.webtrends.harness.component.akkahttp.util.TestBaseCommand
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future


class AkkaHttpGetTest extends FunSuite with PropertyChecks with MustMatchers with ScalatestRouteTest {

  test("should return InternalServerError when command fails") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.failed(new Exception("error"))
    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.InternalServerError
    }
  }

  test("should respond with NoContent when command respond with no data") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.successful(CommandResponse(None))
    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.NoContent
    }
  }

  test("should be able to create a map of query params") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.successful(AkkaHttpCommandResponse(bean
          .get.getValue[Map[String, String]](AkkaHttpBase.QueryParams)
          .map(_.apply("testParam").asInstanceOf[T])))
    }

    Get("/test?testParam=meow") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"meow\""
    }
  }

  test("should override status code on return entity") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.successful(AkkaHttpCommandResponse[T](Some("meow".asInstanceOf[T]), statusCode = Some(StatusCodes.Created)))
    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.Created
      entityAs[String] mustEqual "\"meow\""
    }
  }

  test("should pass back headers on response object") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.successful(AkkaHttpCommandResponse[T](Some("meow".asInstanceOf[T]),
          statusCode = Some(StatusCodes.Created), headers =
            Seq(HttpHeader.parse("fakeHeader", "fakeValue").asInstanceOf[Ok].header)))
    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.Created
      entityAs[String] mustEqual "\"meow\""
      header("fakeHeader").get mustEqual HttpHeader.parse("fakeHeader", "fakeValue").asInstanceOf[Ok].header
    }
  }

  test("should override status code on return none") {
    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"
      override def addRoute(r: Route): Unit = routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] =
        Future.successful(AkkaHttpCommandResponse[T](None, statusCode = Some(StatusCodes.Continue)))
    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.Continue
    }
  }
}
