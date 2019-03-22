package com.webtrends.harness.component.akkahttp.client.oauth.strategy

import akka.NotUsed
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.scaladsl.Source
import com.webtrends.harness.component.akkahttp.client.oauth.config.ConfigLike
import com.webtrends.harness.component.akkahttp.client.oauth.token.GrantType

abstract class Strategy[A <: GrantType](val grant: A) {
  def getAuthorizeUrl(config: ConfigLike, params: Map[String, String]): Option[Uri]
  def getAccessTokenSource(config: ConfigLike, params: Map[String, String], headers: Map[String, String]): Source[HttpRequest, NotUsed]

  protected def getHeaders(headers: Map[String, String]): List[RawHeader] = {
    headers.map{ case(k,v) => RawHeader(k,v) }.toList
  }
}
