/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.akkahttp

import akka.actor.ActorRef
import akka.http.scaladsl.settings.ServerSettings
import com.typesafe.config.Config
import com.webtrends.harness.component.Component
import com.webtrends.harness.utils.ConfigUtil

trait AkkaHttp {
  this: Component =>

  val settings = AkkaHttpSettings(config)

  var internalAkkaHttpRef: Option[ActorRef] = None
  var externalAkkaHttpRef: Option[ActorRef] = None

  def startAkkaHttp() = {
    internalAkkaHttpRef = Some(context.actorOf(InternalAkkaHttpActor.props(settings.internal), AkkaHttp.InternalAkkaHttpName))
    if (settings.external.enabled) {
      externalAkkaHttpRef = Some(context.actorOf(ExternalAkkaHttpActor.props(settings.external), AkkaHttp.externalAkkaHttpName))
    }
  }

  def stopAkkaHttp() = {
    Seq(internalAkkaHttpRef, externalAkkaHttpRef).flatten.foreach(_ ! AkkaHttpUnbind)
  }

}

final case class InternalAkkaHttpSettings(interface: String, port: Int, serverSettings: ServerSettings)
final case class ExternalAkkaHttpSettings(enabled: Boolean, interface: String, port: Int, serverSettings: ServerSettings)
final case class AkkaHttpSettings(internal: InternalAkkaHttpSettings, external: ExternalAkkaHttpSettings)

object AkkaHttpSettings {
  def apply(config: Config): AkkaHttpSettings = {
    val internalPort = ConfigUtil.getDefaultValue("wookiee-akka-http.internal-server.http-port", config.getInt, 8080)
    val internalInterface = ConfigUtil.getDefaultValue("wookiee-akka-http.internal-server.interface", config.getString, "0.0.0.0")

    val externalServerEnabled = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.external-server.enabled", config.getBoolean, false)
    val externalPort = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.external-server.http-port", config.getInt, 8082)
    val externalInterface = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.external-server.interface", config.getString, "0.0.0.0")

    val serverSettings = ServerSettings(config)

    AkkaHttpSettings(
      InternalAkkaHttpSettings(internalInterface, internalPort, serverSettings),
      ExternalAkkaHttpSettings(externalServerEnabled, externalInterface, externalPort, serverSettings)
    )
  }
}

object AkkaHttp {
  val externalAkkaHttpName = "ExternalAkkaHttp"
  val InternalAkkaHttpName = "InternalAkkaHttp"
}