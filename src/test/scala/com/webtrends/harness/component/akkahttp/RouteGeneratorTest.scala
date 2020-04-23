package com.webtrends.harness.component.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToEntityMarshallers
import akka.http.scaladsl.model.{HttpMethods, _}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpRequest, RouteGenerator}
import com.webtrends.harness.logging.Logger
import org.scalatest.WordSpec
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import scala.concurrent.duration._

class RouteGeneratorTest extends WordSpec with ScalatestRouteTest with PredefinedToEntityMarshallers {

  implicit val actorSystem: ActorSystem = ActorSystem("test")
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)


  "RouteGenerator " should {

    def actorRef = actorSystem.actorOf(SimpleCommandActor())
    def requestHandler(req: AkkaHttpRequest) = Future.successful(req)
    def responseHandler200(resp: AkkaHttpRequest) = complete(StatusCodes.OK, resp.toString)
    def rejectionHandler: PartialFunction[Throwable, Route] = {
      case t: Throwable => complete(t.getMessage)
    }

    val failMessage = "purposeful fail"
    def errorOnResponse(echoed: AkkaHttpRequest): Route = {
      if (true) throw new Exception(failMessage)
      complete(StatusCodes.InternalServerError, "should not have returned route")
    }

    "add simple route" in {
      val r = RouteGenerator.makeHttpRoute("getTest", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler200, rejectionHandler)
      Get("/getTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "getTest")
      }
    }
    "route with path segments" in {
      val r = RouteGenerator.makeHttpRoute("getTest/$id", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler200, rejectionHandler)
      Get("/getTest/123") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "123")
      }
    }
    "route with path segments and query params" in {
      val r = RouteGenerator.makeHttpRoute("getTest/$id", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler200, rejectionHandler)
      Get("/getTest/123?enable=true") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "123")
        assert(entityAs[String] contains "true")
      }
    }
    "route with post method" in {
      val r = RouteGenerator.makeHttpRoute("postTest", HttpMethods.POST, Seq(), false, actorRef, requestHandler, responseHandler200, rejectionHandler)
      Post("/postTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "postTest")
      }
    }

    "route with error in response handler" in {
      val r = RouteGenerator.makeHttpRoute("errorTest", HttpMethods.GET, Seq(), false, actorRef, requestHandler, errorOnResponse, rejectionHandler)
      Get("/errorTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains failMessage)
      }
    }
  }
}
