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
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.component.akkahttp.ExternalAkkaHttpSettings
import com.webtrends.harness.component.akkahttp.client.SimpleHttpClient
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import org.joda.time.{DateTime, DateTimeZone}
import com.webtrends.harness.utils.FutureExtensions._
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ExternalAkkaHttpActor {
  def props(settings: ExternalAkkaHttpSettings) = {
    Props(classOf[ExternalAkkaHttpActor], settings.port, settings.interface, settings.serverSettings)
  }
}

class ExternalAkkaHttpActor(port: Int, interface: String, settings: ServerSettings) extends HActor with SimpleHttpClient {
  implicit val system = context.system
  override implicit val executionContext = context.dispatcher
  override implicit val materializer = ActorMaterializer()

  def serverName = "akka-http external-server"
  val serverSource = Http().bind(interface, port, settings = settings)
  val pingUrl = s"http://$interface:$port/ping"

  val baseRoutes =
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
      path("ping") {
        complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
      }
    }

  val bindingFuture = serverSource
    .to(Sink.foreach { conn => conn.handleWith(RouteResult.route2HandlerFlow(routes)) })
    .run()

  bindingFuture.onComplete {
    case Success(s) =>
      log.info(s"$serverName bound to port $port on interface $interface")
    case Failure(f) =>
      log.error(s"Failed to bind akka-http external-server: $f")
  }

  def unbind = bindingFuture.flatMap(_.unbind())

  def routes = if (ExternalAkkaHttpRouteContainer.isEmpty) {
    log.error("no routes defined")
    reject()
  } else {
    ExternalAkkaHttpRouteContainer.getRoutes.foldLeft(baseRoutes)(_ ~ _)
  }

  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }

  override def checkHealth : Future[HealthComponent] = {
      getPing(pingUrl).mapAll {
        case Success(true) =>
          HealthComponent(self.path.toString, ComponentState.NORMAL, s"Healthy: Ping to $pingUrl.")
        case Success(false) =>
          HealthComponent(self.path.toString, ComponentState.CRITICAL, s"Failed to ping server at $pingUrl.")
        case Failure(_) =>
          HealthComponent(self.path.toString, ComponentState.CRITICAL, s"Unexpected error pinging server.")
      }
  }

}
