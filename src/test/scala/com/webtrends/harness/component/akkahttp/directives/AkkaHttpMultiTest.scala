package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean, CommandResponse}
import com.webtrends.harness.component.akkahttp.util.TestEntity
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpMulti, Endpoint}
import com.webtrends.harness.logging.Logger
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future


class AkkaHttpMultiTest extends FunSuite with PropertyChecks with MustMatchers with ScalatestRouteTest with PredefinedToEntityMarshallers {
  test("should handle multiple endpoints") {
    var routes = Set.empty[Route]

    new AkkaHttpMulti with BaseCommand {
      override def allPaths = List(Endpoint("getTest", HttpMethods.GET),
        Endpoint("postTest", HttpMethods.POST, Some(classOf[TestEntity])),
        Endpoint("two/strings", HttpMethods.GET))
      override def addRoute(r: Route): Unit =
        routes += r
      override def execute[T : Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        val path = bean.get.getValue[String](AkkaHttpBase.Path).getOrElse("")
        val method = bean.get.getValue[HttpMethod](AkkaHttpBase.Method).getOrElse(HttpMethods.GET)
        (path, method) match {
          case ("getTest", HttpMethods.GET) =>
            Future.successful(CommandResponse(Some("getted".asInstanceOf[T])))
          case ("postTest", HttpMethods.POST) =>
            Future.successful(CommandResponse(bean.get.getValue[TestEntity](CommandBean.KeyEntity).map(_.asInstanceOf[T])))
          case ("two/strings", HttpMethods.GET) =>
            Future.successful(CommandResponse(Some("getted2".asInstanceOf[T])))
        }
      }

      override protected val log = Logger.getLogger(getClass)
    }

    import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

    Get("/getTest") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
    }
    val entity = TestEntity("meow", 0.1)
    Post("/postTest", entity) ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[TestEntity] mustEqual entity
    }
    Get("/two/strings") ~> routes.reduceLeft(_ ~ _) ~> check {
      status mustEqual StatusCodes.OK
      entityAs[String] mustEqual "\"getted2\""
    }
  }
}
