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

  val akkaHttpServerSettings = ServerSettings(config)
  val internalHttpPort = ConfigUtil.getDefaultValue(s"wookiee-akka-http.http-port", config.getInt, 7070)

  var AkkaHttpRef: Option[ActorRef] = None

  def startAkkaHttp: ActorRef = {
    val ref = context.actorOf(AkkaHttpActor.props, AkkaHttp.AkkaHttpName)
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