package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.headers.{HttpOrigin, Origin, _}
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpCommandResponse
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpDelete, AkkaHttpGet, AkkaHttpPost}
import com.webtrends.harness.component.akkahttp.util.TestBaseCommand
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Inside, MustMatchers, WordSpec}

import scala.collection._
import scala.concurrent.Future

class AkkaHttpCORSTest extends WordSpec with PropertyChecks with MustMatchers with ScalatestRouteTest with Inside {


  "CORS resource request default behavior" should {

    var defaultRoutes = Set.empty[Route]
    new AkkaHttpGet with AkkaHttpCORS with TestBaseCommand {
      override def path: String = "test"

      override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        Future.successful(AkkaHttpCommandResponse(None))
      }

      override def addRoute(r: Route): Unit = defaultRoutes += r
    }

    "Allow request to pass through but do not insert CORS response headers when Origin request header is missing" in {


      Get("/test") ~> defaultRoutes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.NoContent

        headers must (not contain `Access-Control-Allow-Methods`)
        headers must (not contain `Access-Control-Allow-Origin`)
        headers must (not contain `Access-Control-Allow-Credentials`)
        headers must (not contain `Access-Control-Max-Age`)
        headers must (not contain `Access-Control-Allow-Headers`)
        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }

    "Include default response headers when Origin request header is present" in {
      Get("/test") ~> Origin(HttpOrigin("http://www.foo.test")) ~> defaultRoutes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.NoContent

        headers must contain(`Access-Control-Allow-Origin`(HttpOrigin("http://www.foo.test")))
        headers must contain(`Access-Control-Allow-Credentials`(true))
        headers must (not contain `Access-Control-Max-Age`)
        headers must (not contain `Access-Control-Allow-Headers`)
        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }

    "Include default response headers when Origin request header is present and command throw an exception" in {
      var routes = Set.empty[Route]
      new AkkaHttpPost with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.failed(new Exception("Boom!"))
        }

        override def addRoute(r: Route): Unit = routes += r
      }
      Post("/test") ~> Origin(HttpOrigin("http://www.foo.test")) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.InternalServerError

        headers must contain(`Access-Control-Allow-Origin`(HttpOrigin("http://www.foo.test")))
        headers must contain(`Access-Control-Allow-Credentials`(true))
        headers must (not contain `Access-Control-Max-Age`)
        headers must (not contain `Access-Control-Allow-Headers`)
        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }
  }

  "CORS resource request custom behavior" should {
    "Allow Access-Control-Expose-Headers to be added" in {

      val exposeHeaders = immutable.Seq(`Content-Type`.name, `X-Forwarded-For`.name)

      var routes = Set.empty[Route]
      new AkkaHttpGet with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def corsSettingsByPath(path: String): CorsSettings =
          CorsSettings.defaultSettings.withExposedHeaders(exposeHeaders)

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Get("/test") ~> Origin(HttpOrigin("http://www.foo.test")) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.NoContent

        headers must contain (`Access-Control-Allow-Origin`(HttpOrigin("http://www.foo.test")))
        headers must contain (`Access-Control-Allow-Credentials`(true))
        headers must contain (`Access-Control-Expose-Headers`(exposeHeaders: _*))

        headers must (not contain `Access-Control-Max-Age`)
        headers must (not contain `Access-Control-Allow-Headers`)
      }
    }

    "Reject requests with non matching origin" in {
      var routes = Set.empty[Route]
      new AkkaHttpGet with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def corsSettingsByPath(path: String): CorsSettings = CorsSettings.defaultSettings.withAllowedOrigins(
          HttpOriginMatcher(
            HttpOrigin("http://www.a.com"),
            HttpOrigin("http://www.b.com"),
            HttpOrigin("http://www.c.com")
          )
        )
        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      val notAllowedOrigin = HttpOrigin("http://www.d.com")

      Get("/test") ~> Origin(HttpOrigin("http://www.a.com")) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.NoContent
      }

      Get("/test")
        .withHeaders(List(Origin(notAllowedOrigin))) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.Forbidden
        entityAs[String] mustEqual "CORS: invalid origin 'http://www.d.com'"
      }
    }
  }

  "CORS pre-flight request default behavior" should {

    "Allow request to pass through but do not insert CORS response headers when Origin request header is missing" in {
      var routes = Set.empty[Route]
      new AkkaHttpDelete with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Delete("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.NoContent

        headers must (not contain `Access-Control-Allow-Methods`)
        headers must (not contain `Access-Control-Allow-Origin`)
        headers must (not contain `Access-Control-Allow-Credentials`)
        headers must (not contain `Access-Control-Max-Age`)
        headers must (not contain `Access-Control-Allow-Headers`)
        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }

    "Include default response headers when only `Origin` and `Access-Control-Request-Method` headers are present" in {
      var routes = Set.empty[Route]
      new AkkaHttpDelete with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Options("/test") ~>
        Origin(HttpOrigin("http://www.foo.test")) ~>
        `Access-Control-Request-Method`(HttpMethods.DELETE) ~> routes.reduceLeft(_ ~ _) ~> check {

        status mustEqual StatusCodes.OK

        headers must contain(`Access-Control-Allow-Methods`(HttpMethods.DELETE))
        headers must contain(`Access-Control-Allow-Origin`(HttpOrigin("http://www.foo.test")))
        headers must contain(`Access-Control-Allow-Credentials`(true))
        headers must contain(`Access-Control-Max-Age`(30 * 60))

        headers must (not contain `Access-Control-Allow-Headers`)
        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }

    "Reject requests with incompatible `Access-Control-Request` values" in {
      var routes = Set.empty[Route]
      new AkkaHttpDelete with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Options("/test") ~>
        Origin(HttpOrigin("http://www.foo.test")) ~>
        `Access-Control-Request-Method`(HttpMethods.GET) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.Forbidden
        entityAs[String] mustEqual "CORS: invalid method 'GET'"
      }
    }

    "Echo back Access-Control-Request-Headers in Access-Control-Allow-Headers" in {

      val allowedHeaders = Seq(`Content-Type`.name, `X-Real-Ip`.name)

      var routes = Set.empty[Route]
      new AkkaHttpDelete with AkkaHttpCORS with TestBaseCommand {
        override def path: String = "test"

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(AkkaHttpCommandResponse(None))
        }

        override def addRoute(r: Route): Unit = routes += r
      }

      Options("/test") ~>
        Origin(HttpOrigin("http://www.foo.test")) ~>
        `Access-Control-Request-Method`(HttpMethods.DELETE) ~>
        `Access-Control-Request-Headers`(allowedHeaders: _*) ~> routes.reduceLeft(_ ~ _) ~> check {

        status mustEqual StatusCodes.OK

        headers must contain(`Access-Control-Allow-Methods`(HttpMethods.DELETE))
        headers must contain(`Access-Control-Allow-Origin`(HttpOrigin("http://www.foo.test")))
        headers must contain(`Access-Control-Allow-Credentials`(true))
        headers must contain(`Access-Control-Allow-Headers`(allowedHeaders: _*))
        headers must contain(`Access-Control-Max-Age`(30 * 60))

        headers must (not contain `Access-Control-Expose-Headers`)
      }
    }
  }
}
