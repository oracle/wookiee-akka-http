package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

import scala.collection._

trait AkkaHttpCORS extends AkkaHttpBase {
  this: BaseCommand =>

  override val corsEnabled = true
}

object AkkaHttpCORS {
  def corsSettings(allowedMethods: immutable.Seq[HttpMethod]): CorsSettings =
    CorsSettings.defaultSettings.withAllowedMethods(allowedMethods)
}
