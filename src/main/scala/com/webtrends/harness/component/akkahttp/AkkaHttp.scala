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

  var AkkaHttpRef: Option[ActorRef] = None
  var InternalAkkaHttpRef: Option[ActorRef] = None

  def startAkkaHttp() = {
    val ref = context.actorOf(AkkaHttpActor.props(settings.external), AkkaHttp.AkkaHttpName)
    AkkaHttpRef = Some(ref)
    if (settings.internal.enabled) {
      val internalRef = context.actorOf(InternalAkkaHttpActor.props(settings.internal), AkkaHttp.InternalAkkaHttpName)
      InternalAkkaHttpRef = Some(internalRef)
    }
  }

  def stopAkkaHttp() = {
    Seq(AkkaHttpRef, InternalAkkaHttpRef).flatten.foreach(_ ! AkkaHttpUnbind)
  }

}

final case class InternalAkkaHttpSettings(enabled: Boolean, interface: String, port: Int, serverSettings: ServerSettings)
final case class ExternalAkkaHttpSettings(interface: String, port: Int, serverSettings: ServerSettings)
final case class AkkaHttpSettings(internal: InternalAkkaHttpSettings, external: ExternalAkkaHttpSettings)

object AkkaHttpSettings {
  def apply(config: Config): AkkaHttpSettings = {
    val port = ConfigUtil.getDefaultValue("wookiee-akka-http.server.http-port", config.getInt, 8080)
    val interface = ConfigUtil.getDefaultValue("wookiee-akka-http.server.interface", config.getString, "0.0.0.0")

    val internalServerEnabled = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.internal-server.enabled", config.getBoolean, true)
    val internalPort = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.internal-server.http-port", config.getInt, 8081)
    val internalInterface = ConfigUtil.getDefaultValue(
      "wookiee-akka-http.internal-server.interface", config.getString, "127.0.0.1")

    val serverSettings = ServerSettings(config)

    AkkaHttpSettings(
      InternalAkkaHttpSettings(internalServerEnabled, internalInterface, internalPort, serverSettings),
      ExternalAkkaHttpSettings(interface, port, serverSettings)
    )
  }
}

object AkkaHttp {
  val AkkaHttpName = "AkkaHttp"
  val InternalAkkaHttpName = "InternalAkkaHttp"
}