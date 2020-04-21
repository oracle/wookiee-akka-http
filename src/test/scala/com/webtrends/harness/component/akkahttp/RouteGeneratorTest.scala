package com.webtrends.harness.component.akkahttp

import akka.actor.ActorSystem
import akka.actor.Status.Success
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

    def requestHandler = (req: AkkaHttpRequest) => Future.successful(req)
    def actorRef = actorSystem.actorOf(SimpleCommandActor())
    def responseHandler = (resp: AkkaHttpRequest) => complete(resp.toString)

    "add simple route" in {
      val r = RouteGenerator.makeRoute("getTest", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler)
      Get("/getTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "getTest")
      }
    }
    "route with path segments" in {
      val r = RouteGenerator.makeRoute("getTest/$id", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler)
      Get("/getTest/123") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "123")
      }
    }
    "route with path segments and query params" in {
      val r = RouteGenerator.makeRoute("getTest/$id", HttpMethods.GET, Seq(), false, actorRef, requestHandler, responseHandler)
      Get("/getTest/123?enable=true") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "123")
        assert(entityAs[String] contains "true")
      }
    }
    "route with post method" in {
      val r = RouteGenerator.makeRoute("postTest", HttpMethods.POST, Seq(), false, actorRef, requestHandler, responseHandler)
      Post("/postTest") ~> r ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] contains "postTest")
      }
    }
  }


}
