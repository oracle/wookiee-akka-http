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
