/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:23 PM
 */
package com.webtrends.harness.component.akkahttp

import akka.actor.ActorRef
import com.webtrends.harness.component.Component

trait AkkaHttp { this: Component =>

  var AkkaHttpRef:Option[ActorRef] = None

  def startAkkaHttp : ActorRef = {
    val ref = context.actorOf(AkkaHttpActor.props, AkkaHttp.AkkaHttpName)
    AkkaHttpRef = Some(ref)
    ref ! "bind"
    ref
  }

  def stopAkkaHttp = {
    AkkaHttpRef.foreach(_ ! "unbind")
  }
}

object AkkaHttp {
  val AkkaHttpName = "AkkaHttp"
}