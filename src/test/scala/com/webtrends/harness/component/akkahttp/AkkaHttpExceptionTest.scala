package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypes, MediaTypes, StatusCodes, headers => hdrs}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpGet
import com.webtrends.harness.component.akkahttp.util.{ErrorEntity, TestBaseCommand}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future

class AkkaHttpExceptionTest extends FunSuite
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest {

  val statusCodes = Seq(
    StatusCodes.InternalServerError,
    StatusCodes.OK,
    StatusCodes.NoContent,
    StatusCodes.Forbidden,
    StatusCodes.PreconditionRequired
  )

  test("should marshall exception message to JSON by default") {

    forAll(Arbitrary.arbitrary[String].map(ErrorEntity)) { err: ErrorEntity =>
      var routes = Set.empty[Route]

      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.failed(AkkaHttpException[ErrorEntity](err))
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._
        contentType mustEqual ContentTypes.`application/json`
        status mustEqual StatusCodes.InternalServerError
        entityAs[ErrorEntity] mustEqual err
      }
    }
  }

  test("should allow custom status code") {

    forAll(Arbitrary.arbitrary[String].map(ErrorEntity), Gen.oneOf(statusCodes)) { (err, code) =>
      var routes = Set.empty[Route]

      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.failed(AkkaHttpException[ErrorEntity](err, statusCode = code))
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual code
        if (code.allowsEntity) {
          import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._
          contentType mustEqual ContentTypes.`application/json`
          entityAs[ErrorEntity] mustEqual err
        }
      }
    }
  }

  test("should allow custom headers") {


    import scala.collection.immutable
    val err = ErrorEntity("test error")
    val code = StatusCodes.Unauthorized
    val h = immutable.Seq(hdrs.Cookie("chocolate-chip" -> "cookie"), hdrs.Server("CookieJar"))

    var routes = Set.empty[Route]

    new AkkaHttpGet with TestBaseCommand {
      override def path: String = "test"

      override def addRoute(r: Route): Unit = routes += r

      override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        Future.failed(AkkaHttpException[ErrorEntity](err, statusCode = code, headers = h))
      }

    }

    Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
      import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._
      status mustEqual code
      headers mustEqual h
      entityAs[ErrorEntity] mustEqual err
    }
  }

  test("should allow custom marshaller") {

    forAll(Arbitrary.arbitrary[String].map(ErrorEntity), Gen.oneOf(statusCodes)) { (err, code) =>
      val cType = ContentTypes.`text/plain(UTF-8)`

      var routes = Set.empty[Route]

      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        val m: ToEntityMarshaller[ErrorEntity]  = Marshaller.StringMarshaller.wrap(MediaTypes.`text/plain`) { e: ErrorEntity =>
          e.error
        }

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.failed(AkkaHttpException[ErrorEntity](err, code, marshaller = Some(m)))
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {

        status mustEqual code
        if (code.allowsEntity) {
          implicit def um: FromEntityUnmarshaller[ErrorEntity] =
            Unmarshaller.stringUnmarshaller.forContentTypes(cType).map { str =>
              ErrorEntity(str)
            }
          contentType mustEqual cType
          entityAs[ErrorEntity] mustEqual err
        }
      }
    }
  }

  test("should respond with InternalServerError when marshaller fail") {

    forAll(Arbitrary.arbitrary[String].map(ErrorEntity), Gen.oneOf(statusCodes)) { (err, code) =>

      var routes = Set.empty[Route]

      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        def m: ToEntityMarshaller[ErrorEntity] = throw new Exception("marshaller failed")

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.failed(AkkaHttpException[ErrorEntity](err, code, marshaller = Some(m)))
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.InternalServerError
        entityAs[String] mustEqual StatusCodes.InternalServerError.defaultMessage
      }
    }
  }
}
