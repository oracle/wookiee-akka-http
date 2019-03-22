package com.webtrends.harness.component.akkahttp.client.oauth.strategy

import akka.NotUsed
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import com.webtrends.harness.component.akkahttp.client.oauth.config.ConfigLike
import com.webtrends.harness.component.akkahttp.client.oauth.token.GrantType

class ClientCredentialsStrategy extends Strategy(GrantType.ClientCredentials) {
  override def getAuthorizeUrl(config: ConfigLike, params: Map[String, String] = Map.empty): Option[Uri] = None

  override def getAccessTokenSource(config: ConfigLike, params: Map[String, String] = Map.empty, headers: Map[String, String] = Map.empty): Source[HttpRequest, NotUsed] = {
    val uri = Uri
      .apply(config.getSchemaAndHost)
      .withPath(Uri.Path(config.tokenUrl))

    val request = HttpRequest(
      method = config.tokenMethod,
      uri = uri,
      headers = List(
        RawHeader("Accept", "*/*")
      ) ++ getHeaders(headers),
      FormData(
        params ++ Map(
          "grant_type"    -> grant.value,
          "client_id"     -> config.clientId,
          "client_secret" -> config.clientSecret
        )
      ).toEntity(HttpCharsets.`UTF-8`)
    )

    Source.single(request)
  }
}
