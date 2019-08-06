package com.webtrends.harness.component.akkahttp.client.oauth.strategy

import akka.NotUsed
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, Uri}
import akka.stream.scaladsl.Source
import com.webtrends.harness.component.akkahttp.client.oauth.config.{ConfigLike, OnBody, OnHeader}
import com.webtrends.harness.component.akkahttp.client.oauth.token.GrantType

abstract class Strategy[A <: GrantType](val grant: A) {
  def getAuthorizeUrl(config: ConfigLike, params: Map[String, String]): Option[Uri]
  def getAccessTokenSource(config: ConfigLike, params: Map[String, String], headers: Map[String, String]): Source[HttpRequest, NotUsed]

  protected def getHeaders(headers: Map[String, String]): List[RawHeader] = {
    headers.map{ case(k,v) => RawHeader(k,v) }.toList
  }

  protected def optionalAddClient(params: Map[String, String], config: ConfigLike): Map[String, String] = {
    config.clientLocation match {
      case OnHeader => params
      case _ =>
        params.updated("client_id", config.clientId)
          .updated("client_secret", config.clientSecret)
    }
  }

  protected def optionalAddClient(headers: List[HttpHeader], config: ConfigLike): List[HttpHeader] = {
    config.clientLocation match {
      case OnBody => headers
      case _ =>
        headers.::(Authorization(
          BasicHttpCredentials(config.clientId, config.clientSecret)))
    }
  }
}
