package com.webtrends.harness.component.akkahttp.client.oauth.config

import akka.http.scaladsl.model.{HttpMethod, HttpMethods, Uri}

case class Config(
    clientId: String,
    clientSecret: String,
    site: Uri,
    authorizeUrl: String = "/oauth/authorize",
    tokenUrl: String = "/oauth/token",
    tokenMethod: HttpMethod = HttpMethods.POST
) extends ConfigLike {
  def getHost: String = site.authority.host.address()
  def getPort: Int = site.scheme match {
    case "http"  => if (site.authority.port == -1) 80 else site.authority.port
    case "https" => if (site.authority.port == -1) 443 else site.authority.port
  }
  override def getSchemaAndHost: String = {
    site.scheme + "://" + site.authority.toString()
  }
}
