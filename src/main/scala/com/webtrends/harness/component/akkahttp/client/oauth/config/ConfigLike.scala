package com.webtrends.harness.component.akkahttp.client.oauth.config

import akka.http.scaladsl.model.{HttpMethod, Uri}

trait ConfigLike {
  def clientId: String
  def clientSecret: String
  def site: Uri
  def authorizeUrl: String
  def tokenUrl: String
  def tokenMethod: HttpMethod
  def getHost: String
  def getPort: Int
  def getSchemaAndHost: String
}
