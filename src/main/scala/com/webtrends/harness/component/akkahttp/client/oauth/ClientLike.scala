package com.webtrends.harness.component.akkahttp.client.oauth

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, Uri }
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.webtrends.harness.component.akkahttp.client.oauth.strategy.Strategy

import scala.concurrent.{ ExecutionContext, Future }

trait ClientLike {
  def getAuthorizeUrl[A <: GrantType](grant: A, params: Map[String, String] = Map.empty)(implicit s: Strategy[A]): Option[Uri]

  def getAccessToken[A <: GrantType](
      grant: A,
      params: Map[String, String] = Map.empty,
      headers: Map[String, String] = Map.empty

  )(implicit s: Strategy[A], ec: ExecutionContext, mat: Materializer): Future[Either[Throwable, AccessToken]]

  def getConnectionWithAccessToken(accessToken: AccessToken): Flow[HttpRequest, HttpResponse, _]
}
