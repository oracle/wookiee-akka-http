package com.webtrends.harness.component.akkahttp.client.oauth.strategy

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.scaladsl.Source
import com.webtrends.harness.component.akkahttp.client.oauth.config.ConfigLike
import com.webtrends.harness.component.akkahttp.client.oauth.token.GrantType

class ImplicitStrategy extends Strategy(GrantType.Implicit) {
  override def getAuthorizeUrl(config: ConfigLike, params: Map[String, String] = Map.empty): Option[Uri] = {
    val uri = Uri
      .apply(config.site.toASCIIString)
      .withPath(Uri.Path(config.authorizeUrl))
      .withQuery(Uri.Query(params ++ Map("response_type" -> "token", "client_id" -> config.clientId)))

    Option(uri)
  }

  override def getAccessTokenSource(config: ConfigLike, params: Map[String, String] = Map.empty, headers: Map[String, String] = Map.empty): Source[HttpRequest, NotUsed] =
    Source.empty
}
