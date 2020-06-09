package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.headers.HttpOrigin
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

import scala.collection._

trait AkkaHttpCORS extends AkkaHttpBase {
  this: BaseCommand =>

  override val corsEnabled = true
}

object AkkaHttpCORS {
  def corsSettings(allowedMethods: immutable.Seq[HttpMethod], allowedOrigins: Seq[HttpOrigin]): CorsSettings = {
    val origins = if (allowedOrigins.nonEmpty) HttpOriginMatcher(allowedOrigins: _*) else HttpOriginMatcher.*
    CorsSettings.defaultSettings.withAllowedMethods(allowedMethods)
      .withAllowedOrigins(origins)
  }
}
