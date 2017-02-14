package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.server.Directive0
import ch.megard.akka.http.cors._
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpMethod

import scala.collection._

trait AkkaHttpCORS extends AkkaHttpMethod {
  this: BaseCommand =>

  def corsSettings: CorsSettings = CorsSettings.Default(
    CorsSettings.defaultSettings.allowGenericHttpRequests,
    CorsSettings.defaultSettings.allowCredentials,
    CorsSettings.defaultSettings.allowedOrigins,
    CorsSettings.defaultSettings.allowedHeaders,
    immutable.Seq(method),
    CorsSettings.defaultSettings.exposedHeaders,
    CorsSettings.defaultSettings.maxAge
  )

  override def httpMethod: Directive0 = CorsDirectives.cors(corsSettings) & super.httpMethod
}
