package com.webtrends.harness.component.akkahttp.client

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.webtrends.harness.component.akkahttp.client.oauth._
import com.webtrends.harness.component.akkahttp.client.oauth.config.Config
import com.webtrends.harness.component.akkahttp.client.oauth.token.Error.{InvalidClient, UnauthorizedException}
import com.webtrends.harness.component.akkahttp.client.oauth.token.{AccessToken, GrantType}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{AsyncFlatSpec, MustMatchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class OAuthClientTest extends AsyncFlatSpec
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest {

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), Duration.Inf)
  }

  behavior of "oauthClient"

  it should "#getAuthorizeUrl should delegate processing to strategy" in {
    import strategy._

    val config = Config("xxx", "yyy", site = Uri("https://example.com"), authorizeUrl = "/oauth/custom_authorize")
    val client = OAuthClient(config)
    val result = client.getAuthorizeUrl(GrantType.AuthorizationCode, Map("redirect_uri" -> "https://example.com/callback"))
    val actual = result.get.toString
    val expect = "https://example.com/oauth/custom_authorize?redirect_uri=https://example.com/callback&response_type=code&client_id=xxx"
    assert(actual == expect)
  }

  it should "#getAccessToken return Right[AccessToken] when oauth provider approves" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.OK,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "access_token": "xxx",
           |  "token_type": "bearer",
           |  "expires_in": 86400,
           |  "refresh_token": "yyy"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode, Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isRight)
    }
  }

  it should "return Left[UnauthorizedException] when oauth provider rejects" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.Unauthorized,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "error": "invalid_client",
           |  "error_description": "description"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode, Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isLeft)
      assert(r.left.exists(_.isInstanceOf[UnauthorizedException]))
      assert(r.left.get.asInstanceOf[UnauthorizedException].description.get == "description")
      assert(r.left.get.asInstanceOf[UnauthorizedException].code == InvalidClient)
    }
  }

  it should "not break when error_description is absent" in {
    import strategy._

    val response = HttpResponse(
      status = StatusCodes.Unauthorized,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "error": "invalid_client"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest].map(_ => response)
    val config         = Config("xxx", "yyy", Uri("https://example.com"))
    val client         = OAuthClient(config, mockConnection)
    val result         = client.getAccessToken(GrantType.AuthorizationCode, Map("code" -> "zzz", "redirect_uri" -> "https://example.com"))

    result.map { r =>
      assert(r.isLeft)
      assert(r.left.exists(_.isInstanceOf[UnauthorizedException]))
      assert(r.left.get.asInstanceOf[UnauthorizedException].description.isEmpty)
      assert(r.left.get.asInstanceOf[UnauthorizedException].code == InvalidClient)
    }
  }

  it should "#getConnectionWithAccessToken return outgoing connection flow with access token" in {
    val accessToken = AccessToken(
      access_token = "xxx",
      token_type = "bearer",
      expires_in = 86400,
      refresh_token = Some("yyy"),
      scope = None
    )

    val request = HttpRequest(HttpMethods.GET, "/v1/foo/bar")
    val response = HttpResponse(
      status = StatusCodes.OK,
      headers = Nil,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        s"""
           |{
           |  "key": "value"
           |}
         """.stripMargin
      )
    )

    val mockConnection = Flow[HttpRequest]
      .filter { req =>
        req.headers.exists(_.is("authorization")) && req.headers.exists(_.value() == s"Bearer ${accessToken.access_token}")
      }
      .map(_ => response)

    val config = Config("xxx", "yyy", Uri("https://example.com"))
    val client = OAuthClient(config, mockConnection)
    val result = Source.single(request).via(client.getConnectionWithAccessToken(accessToken)).runWith(Sink.head)

    result.map { r =>
      assert(r.status.isSuccess())
    }
  }

  it should "construct schema and host correctly" in {
    val config         = Config("xxx", "yyy", Uri("https://example.com:8080"))
    assert(config.getSchemaAndHost == "https://example.com:8080")
  }
}
