package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.server.Directive0
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

import scala.collection._

trait AkkaHttpCORS extends AkkaHttpBase {
  this: BaseCommand =>

  def corsSettings: CorsSettings = corsSettings(immutable.Seq(method))
  
  def corsSettings(allowedMethods: immutable.Seq[HttpMethod]): CorsSettings = CorsSettings.Default(
    CorsSettings.defaultSettings.allowGenericHttpRequests,
    CorsSettings.defaultSettings.allowCredentials,
    CorsSettings.defaultSettings.allowedOrigins,
    CorsSettings.defaultSettings.allowedHeaders,
    allowedMethods,
    CorsSettings.defaultSettings.exposedHeaders,
    CorsSettings.defaultSettings.maxAge
  )

  override def httpMethod(method: HttpMethod): Directive0 = CorsDirectives.cors(corsSettings) & super.httpMethod(method)
}
