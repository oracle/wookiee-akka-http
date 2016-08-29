package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.component.spray.route.RouteManager
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.HttpHeaders.Authorization
import spray.http._
import spray.routing.{Directives, HttpService}
import spray.testkit.Specs2RouteTest

class AuthDirectiveSpec extends SpecificationWithJUnit with Directives with Specs2RouteTest with HttpService {
  def actorRefFactory = system

  val testCommandRef = TestActorRef[AuthTestCommand]
  val testActor = testCommandRef.underlyingActor

  "Auth command" should {
    "should handle Get requests using basic auth" in {
      HttpRequest(
        HttpMethods.GET,
        "/foo/key1/bar/key2",
        List(Authorization(BasicHttpCredentials("good", "whatever"))),
        None
      ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
        status.intValue mustEqual 200
        header("WWW-Authenticate").isEmpty mustEqual true
      }
    }

    "should fail Get requests using wrong basic creds" in {
      HttpRequest(
        HttpMethods.GET,
        "/foo/key1/bar/key2",
        List(Authorization(BasicHttpCredentials("bad", "whatever"))),
        None
      ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
        status.intValue mustEqual 401
        header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
      }
    }

    "should fail Get requests using no creds" in {
      Get("/foo/key1/bar/key2") ~> RouteManager.getRoute("AuthTest_get").get ~> check {
        status.intValue mustEqual 401
        header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
      }
    }

    "should handle Get requests using token auth" in {
      HttpRequest(
        HttpMethods.GET,
        "/foo/key1/bar/key2",
        List(Authorization(OAuth2BearerToken("good"))),
        None
      ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
        status.intValue mustEqual 200
        header("WWW-Authenticate").isEmpty mustEqual true
      }
    }

    "should fail Get requests using wrong token creds" in {
      HttpRequest(
        HttpMethods.GET,
        "/foo/key1/bar/key2",
        List(Authorization(OAuth2BearerToken("bad"))),
        None
      ) ~> RouteManager.getRoute("AuthTest_get").get ~> check {
        status.intValue mustEqual 401
        header("WWW-Authenticate").get.value mustEqual "Basic realm=session"
      }
    }
  }
}
