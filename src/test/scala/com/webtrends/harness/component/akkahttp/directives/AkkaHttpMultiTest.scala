package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpOrigin, Origin, `Access-Control-Request-Method`}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean, CommandResponse}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase._
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpMulti, Endpoint}
import com.webtrends.harness.component.akkahttp.util.TestEntity
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future


class AkkaHttpMultiTest extends FunSuite with PropertyChecks with MustMatchers with ScalatestRouteTest with PredefinedToEntityMarshallers {

  test("should handle multiple endpoints") {
    var routes = collection.mutable.LinkedHashSet[Route]()

    new AkkaHttpMulti with BaseCommand {
      override def allPaths = List(
        Endpoint("getTest", HttpMethods.GET),
        Endpoint("postTest", HttpMethods.POST, classOf[TestEntity]),
        Endpoint("two/strings", HttpMethods.GET),
        Endpoint("two/strings/$count", HttpMethods.GET),
        Endpoint("separated/$arg1/args/$arg2", HttpMethods.GET),
        Endpoint("one/$a1/two/$a2/three/$3/four", HttpMethods.GET),
        Endpoint("$ver/one/$a1/two/$a2/three/$3/$four/$last", HttpMethods.GET))
      override def addRoute(r: Route): Unit =
        routes += r

      override def process(bean: CommandBean): PartialFunction[(String, HttpMethod), Future[BaseCommandResponse[_]]] = {
        case ("getTest", HttpMethods.GET) =>
          val meowVal = getQueryParams(bean).getOrElse("meow", "")
          Future.successful(CommandResponse(Some(s"getted$meowVal")))
        case ("postTest", HttpMethods.POST) =>
          val meowVal = bean.getValue[String]("meow").getOrElse("")
          val payload = getPayload[TestEntity](bean)
          val load = payload.map(te => te.copy(te.v0 + meowVal, te.v1)).getOrElse(TestEntity("default1", 0.2))
          Future.successful(CommandResponse(Some(load)))
        case ("two/strings", HttpMethods.GET) =>
          Future.successful(CommandResponse(Some("getted2")))
        case ("two/strings/$count", HttpMethods.GET) =>
          Future.successful(CommandResponse(bean.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
            holder._1)))
        case ("separated/$arg1/args/$arg2", HttpMethods.GET) =>
          Future.successful(CommandResponse(bean.getValue[Holder2](AkkaHttpBase.Segments).map(holder =>
            holder._1 + holder._2)))
        case ("one/$a1/two/$a2/three/$3/four", HttpMethods.GET) =>
          val params = getURIParams[Holder3](bean)
          Future.successful(CommandResponse(Some(params._1 + params._2 + params._3)))
        case ("$ver/one/$a1/two/$a2/three/$3/$four/$last", HttpMethods.GET) =>
          val params = getURIParams[Holder6](bean)
          Future.successful(CommandResponse(Some(bean("ver") + params._2 + params._3 + params._4 + bean("four") + params._6)))
      }
    }

    import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

    Get("/getTest/") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"getted\""
    }
    Get("/getTest/?meow=true") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"gettedtrue\""
    }
    HttpRequest(HttpMethods.GET, "/getTest/?meow=true").withHeaders(
      Origin(HttpOrigin("http://meow.com"))) ~> routes.reduceLeft(_ ~ _) ~> check {
      val h = headers
      h.find(_.name() == "Access-Control-Allow-Credentials").get.value() mustEqual "true"
      h.find(_.name() == "Access-Control-Allow-Origin").get.value() mustEqual "http://meow.com"
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"gettedtrue\""
    }
    val entity = TestEntity("meow", 0.1)
    Post("/postTest", entity) ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[TestEntity] mustEqual entity
    }
    Post("/postTest") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[TestEntity] mustEqual TestEntity("default1", 0.2)
    }
    Post("/postTest?meow=test", entity) ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[TestEntity] mustEqual TestEntity("meowtest", 0.1)
    }
    Get("/two/strings") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"getted2\""
    }
    Get("/two/strings/5") ~> routes.reduceLeft(_ ~ _) ~> check {
      entityAs[String] mustEqual "\"5\""
    }
    Get("/separated/5/args/10") ~> routes.reduceLeft(_ ~ _) ~> check {
      entityAs[String] mustEqual "\"510\""
    }
    Get("/one/1/two/2/three/3/four") ~> routes.reduceLeft(_ ~ _) ~> check {
      entityAs[String] mustEqual "\"123\""
    }
    // trailing slash test
    Get("/getTest/") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
    }
    Get("/v1/one/1/two/2/three/3/4/final") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"v11234final\""
    }
  }

  test("should respect endpoint addition order") {
    val routes = getToughRoutes()

    Get("/two/strings") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"getted2\""
    }
    Get("/two/arg") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"arg\""
    }
    Get("/one/strings") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"getted1\""
    }
    Get("/one/arg1") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"arg1\""
    }
  }

  test("should support CORS by default on all endpoints") {
    val routes = getToughRoutes()

    Options("/two/strings").withHeaders(
      Origin(HttpOrigin("http://domain-a.com")),
      `Access-Control-Request-Method`(HttpMethods.GET)
    ) ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      header("Access-Control-Allow-Origin").get.value() mustEqual "http://domain-a.com"
      header("Access-Control-Allow-Credentials").get.value() mustEqual "true"
      header("Access-Control-Allow-Methods").get.value() mustEqual "GET"
    }

    Options("/two/arg").withHeaders(
      Origin(HttpOrigin("http://domain-a.com")),
      `Access-Control-Request-Method`(HttpMethods.GET)
    ) ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      header("Access-Control-Allow-Origin").get.value() mustEqual "http://domain-a.com"
      header("Access-Control-Allow-Credentials").get.value() mustEqual "true"
      header("Access-Control-Allow-Methods").get.value() mustEqual "GET, POST"
    }
  }

  test("give 415 responses for bad json every time, instead of 405") {
    val routes = collection.mutable.LinkedHashSet[Route]()

    new AkkaHttpMulti with BaseCommand {
      override def allPaths = List(
        Endpoint("same/path", HttpMethods.GET),
        Endpoint("same/path", HttpMethods.POST, classOf[TestEntity]),
        Endpoint("same/path", HttpMethods.PUT, classOf[TestEntity]),
        Endpoint("same/path", HttpMethods.DELETE))

      override def addRoute(r: Route): Unit =
        routes.add(r)

      override def process(bean: CommandBean): PartialFunction[(String, HttpMethod), Future[BaseCommandResponse[_]]] = {
        case ("same/path", HttpMethods.POST) =>
          Future.successful(CommandResponse(Some("POST")))
        case ("same/path", HttpMethods.PUT) =>
          Future.successful(CommandResponse(Some("PUT")))
        case ("same/path", HttpMethods.DELETE) =>
          Future.successful(CommandResponse(Some("DELETE")))
        case ("same/path", HttpMethods.GET) =>
          Future.successful(CommandResponse(Some("GET")))
      }
    }

    Get("/same/path") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"GET\""
    }
    Post("/same/path", "badRequest") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.UnsupportedMediaType
    }
    Put("/same/path", "badRequest") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.UnsupportedMediaType
    }
    Delete("/same/path") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"DELETE\""
    }
  }

  test("Clean the bean!") {
    val dirtyBean = CommandBean(Map(Segments -> "", Params -> "", Auth -> "", Method -> "", "clean" -> "true"))
    val cleanBean = beanClean(dirtyBean)
    cleanBean.size mustEqual 1
    cleanBean("clean") mustEqual "true"
  }

  test("parse headers") {
    parseHeader(":invalid", ":value").isEmpty mustEqual true
    parseHeader("Content-Type", "application/json").isDefined mustEqual true
  }

  test("get headers") {
    val routes = getToughRoutes()
    val header: HttpHeader = HttpHeader.parse("Header", "headValue") match {
      case ParsingResult.Ok(head, _) => head
      case ParsingResult.Error(err) => throw new IllegalStateException(err.detail)
    }
    Get("/header").addHeader(header) ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"headValue\""
    }
  }

  private def getToughRoutes(corsExt: Boolean = false): List[Route] = {
    val routes = collection.mutable.LinkedHashSet[Route]()

    class TestMulti extends AkkaHttpMulti with BaseCommand {
      override def allPaths = List(
        Endpoint("two/strings", HttpMethods.GET),
        Endpoint("two/$arg", HttpMethods.GET),
        Endpoint("one/strings", HttpMethods.GET),
        Endpoint("one/$arg", HttpMethods.GET),
        Endpoint("two/$arg", HttpMethods.POST),
        Endpoint("header", HttpMethods.GET))

      override def addRoute(r: Route): Unit =
        routes.add(r)

      override def process(bean: CommandBean): PartialFunction[(String, HttpMethod), Future[BaseCommandResponse[_]]] = {
        case ("two/strings", HttpMethods.GET) =>
          Future.successful(CommandResponse(Some("getted2")))
        case ("two/$arg", HttpMethods.GET) =>
          Future.successful(CommandResponse(bean.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
            holder._1)))
        case ("one/strings", HttpMethods.GET) =>
          Future.successful(CommandResponse(Some("getted1")))
        case ("one/$arg", HttpMethods.GET) =>
          Future.successful(CommandResponse(bean.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
            holder._1)))
        case ("header", HttpMethods.GET) =>
          val header = getHeader(bean, "Header")
          Future.successful(CommandResponse(Some(header.get)))
      }
    }

    if (corsExt) new TestMulti with AkkaHttpCORS
    else new TestMulti

    routes.toList
  }
}
