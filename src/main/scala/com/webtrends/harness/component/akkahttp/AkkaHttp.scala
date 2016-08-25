/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.akkahttp

import akka.actor.ActorRef
import akka.http.scaladsl.settings.ServerSettings
import com.webtrends.harness.component.Component
import com.webtrends.harness.utils.ConfigUtil

trait AkkaHttp {
  this: Component =>

  val port = ConfigUtil.getDefaultValue(s"wookiee-akka-http.http-port", config.getInt, 7070)
  val interface = ConfigUtil.getDefaultValue(s"wookiee-akka-http.interface", config.getString, "0.0.0.0")
  val akkaHttpServerSettings = ServerSettings(config)

  var AkkaHttpRef: Option[ActorRef] = None

  def startAkkaHttp: ActorRef = {
    val ref = context.actorOf(AkkaHttpActor.props(port, interface, akkaHttpServerSettings), AkkaHttp.AkkaHttpName)
    AkkaHttpRef = Some(ref)
    ref
  }

  def stopAkkaHttp = {
    AkkaHttpRef.foreach(_ ! AkkaHttpUnbind)
  }

}

object AkkaHttp {
  val AkkaHttpName = "AkkaHttp"
}