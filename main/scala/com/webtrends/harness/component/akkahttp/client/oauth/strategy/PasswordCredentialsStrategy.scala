package com.webtrends.harness.component.akkahttp.client.oauth.strategy

import akka.NotUsed
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import com.webtrends.harness.component.akkahttp.client.oauth.config.ConfigLike
import com.webtrends.harness.component.akkahttp.client.oauth.token.GrantType

class PasswordCredentialsStrategy extends Strategy(GrantType.PasswordCredentials) {
  override def getAuthorizeUrl(config: ConfigLike, params: Map[String, String] = Map.empty): Option[Uri] = None

  override def getAccessTokenSource(config: ConfigLike, params: Map[String, String] = Map.empty, headers: Map[String, String] = Map.empty): Source[HttpRequest, NotUsed] = {
    require(params.contains("username"))
    require(params.contains("password"))

    val uri = Uri
      .apply(config.getSchemaAndHost)
      .withPath(Uri.Path(config.tokenUrl))

    val request = HttpRequest(
      method = config.tokenMethod,
      uri = uri,
      headers = getHeaders(headers) ++ optionalAddClient(List(
        RawHeader("Accept", "*/*")), config),
      FormData(
        params ++ optionalAddClient(Map(
          "grant_type" -> grant.value
        ), config)
      ).toEntity(HttpCharsets.`UTF-8`)
    )

    Source.single(request)
  }
}
