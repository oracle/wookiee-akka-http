package com.webtrends.harness.component.akkahttp.directives

import java.util.Collections

import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean, CommandResponse}
import com.webtrends.harness.component.akkahttp.util.TestEntity
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpMulti, Endpoint}
import com.webtrends.harness.logging.Logger
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.collection.JavaConversions._
import scala.concurrent.Future


class AkkaHttpMultiTest extends FunSuite with PropertyChecks with MustMatchers with ScalatestRouteTest with PredefinedToEntityMarshallers {
  test("should handle multiple endpoints") {
    var routes = Set.empty[Route]

    new AkkaHttpMulti with BaseCommand {
      override def allPaths = List(Endpoint("getTest", HttpMethods.GET),
        Endpoint("postTest", HttpMethods.POST, Some(classOf[TestEntity])),
        Endpoint("two/strings", HttpMethods.GET),
        Endpoint("two/strings/$count", HttpMethods.GET),
        Endpoint("separated/$arg1/args/$arg2", HttpMethods.GET),
        Endpoint("one/$a1/two/$a2/three/$3/four", HttpMethods.GET))
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
          case ("two/strings/$count", HttpMethods.GET) =>
            Future.successful(CommandResponse(bean.get.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
              holder._1.asInstanceOf[T])))
          case ("separated/$arg1/args/$arg2", HttpMethods.GET) =>
            Future.successful(CommandResponse(bean.get.getValue[Holder2](AkkaHttpBase.Segments).map(holder =>
              (holder._1 + holder._2).asInstanceOf[T])))
          case ("one/$a1/two/$a2/three/$3/four", HttpMethods.GET) =>
            Future.successful(CommandResponse(bean.get.getValue[Holder3](AkkaHttpBase.Segments).map(holder =>
              (holder._1 + holder._2 + holder._3).asInstanceOf[T])))
        }
      }

      override protected val log = Logger.getLogger(getClass)
    }

    import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

    Get("/getTest/") ~> routes.reduceLeft(_ ~ _) ~> check {
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
  }

  test("should respect endpoint addition order") {
    forAll { ent: Int =>
      var routes = Collections.synchronizedSet[Route](new java.util.LinkedHashSet[Route]())

      new AkkaHttpMulti with BaseCommand {
        override def allPaths = List(
          Endpoint("two/strings", HttpMethods.GET),
          Endpoint("two/$arg", HttpMethods.GET),
          Endpoint("one/strings", HttpMethods.GET),
          Endpoint("one/$arg", HttpMethods.GET))

        override def addRoute(r: Route): Unit =
          routes.add(r)

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val path = bean.get.getValue[String](AkkaHttpBase.Path).getOrElse("")
          val method = bean.get.getValue[HttpMethod](AkkaHttpBase.Method).getOrElse(HttpMethods.GET)
          (path, method) match {
            case ("two/strings", HttpMethods.GET) =>
              Future.successful(CommandResponse(Some("getted2".asInstanceOf[T])))
            case ("two/$arg", HttpMethods.GET) =>
              Future.successful(CommandResponse(bean.get.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
                holder._1.asInstanceOf[T])))
            case ("one/strings", HttpMethods.GET) =>
              Future.successful(CommandResponse(Some("getted1".asInstanceOf[T])))
            case ("one/$arg", HttpMethods.GET) =>
              Future.successful(CommandResponse(bean.get.getValue[Holder1](AkkaHttpBase.Segments).map(holder =>
                holder._1.asInstanceOf[T])))
          }
        }

        override protected val log = Logger.getLogger(getClass)
      }

      import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

      Get("/two/strings") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        entityAs[String] mustEqual "\"getted2\""
      }
      Get("/two/arg") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        entityAs[String] mustEqual "\"arg\""
      }
      Get("/one/strings") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        entityAs[String] mustEqual "\"getted1\""
      }
      Get("/one/arg1") ~> routes.toSet.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        entityAs[String] mustEqual "\"arg1\""
      }
    }
  }

  test("give 415 responses for bad json every time, instead of 405") {
    val routes = Collections.synchronizedSet[Route](new java.util.LinkedHashSet[Route]())

    new AkkaHttpMulti with BaseCommand {
      override def allPaths = List(
        Endpoint("same/path", HttpMethods.GET),
        Endpoint("same/path", HttpMethods.POST, Some(classOf[TestEntity])),
        Endpoint("same/path", HttpMethods.PUT, Some(classOf[TestEntity])),
        Endpoint("same/path", HttpMethods.DELETE))

      override def addRoute(r: Route): Unit =
        routes.add(r)

      override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        val path = bean.get.getValue[String](AkkaHttpBase.Path).getOrElse("")
        val method = bean.get.getValue[HttpMethod](AkkaHttpBase.Method).getOrElse(HttpMethods.GET)
        (path, method) match {
          case ("same/path", HttpMethods.POST) =>
            Future.successful(CommandResponse(Some("POST".asInstanceOf[T])))
          case ("same/path", HttpMethods.PUT) =>
            Future.successful(CommandResponse(Some("PUT".asInstanceOf[T])))
          case ("same/path", HttpMethods.DELETE) =>
            Future.successful(CommandResponse(Some("DELETE".asInstanceOf[T])))
          case ("same/path", HttpMethods.GET) =>
            Future.successful(CommandResponse(Some("GET".asInstanceOf[T])))
        }
      }

      override protected val log = Logger.getLogger(getClass)
    }

    import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

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
}
