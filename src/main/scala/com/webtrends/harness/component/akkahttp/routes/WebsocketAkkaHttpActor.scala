/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.component.akkahttp.routes

import akka.actor.Props
import akka.http.scaladsl.server.Directives.reject
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.webtrends.harness.component.akkahttp.WebsocketAkkaHttpSettings
import com.webtrends.harness.health.{ComponentState, HealthComponent}

import scala.concurrent.Future

object WebsocketAkkaHttpActor {
  def props(settings: WebsocketAkkaHttpSettings): Props = {
    Props(WebsocketAkkaHttpActor(settings.port, settings.interface,
      settings.httpsPort, settings.serverSettings))
  }
}

case class WebsocketAkkaHttpActor(port: Int, interface: String, httpsPort: Option[Int],
                                  settings: ServerSettings) extends AkkaHttpActor {

  override def routes: Route = if (WebsocketAkkaHttpRouteContainer.isEmpty) {
    log.debug("no routes defined")
    reject()
  } else {
    WebsocketAkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
  }

  override def checkHealth: Future[HealthComponent] = {
    Future.successful(HealthComponent("WebsocketAkkaHttpActor", ComponentState.NORMAL, "Websocket Actor Up"))
  }
}
