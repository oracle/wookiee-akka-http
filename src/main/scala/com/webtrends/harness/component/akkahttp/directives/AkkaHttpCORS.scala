package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.{BaseCommand, Command, MapBean}
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}

import scala.collection._

trait AkkaHttpCORS extends AkkaHttpBase { this: Command[MapBean, AkkaHttpCommandResponse[_]] =>

  override val corsEnabled = true
}

object AkkaHttpCORS {
  def corsSettings(allowedMethods: immutable.Seq[HttpMethod]): CorsSettings = CorsSettings.Default(
    CorsSettings.defaultSettings.allowGenericHttpRequests,
    CorsSettings.defaultSettings.allowCredentials,
    CorsSettings.defaultSettings.allowedOrigins,
    CorsSettings.defaultSettings.allowedHeaders,
    allowedMethods,
    CorsSettings.defaultSettings.exposedHeaders,
    CorsSettings.defaultSettings.maxAge
  )


}
