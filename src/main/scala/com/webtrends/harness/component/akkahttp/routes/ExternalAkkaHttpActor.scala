package com.webtrends.harness.component.akkahttp.routes

import akka.actor.Props
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import com.webtrends.harness.component.akkahttp.ExternalAkkaHttpSettings
import org.joda.time.{DateTime, DateTimeZone}

object ExternalAkkaHttpActor {
  def props(settings: ExternalAkkaHttpSettings): Props = {
    Props(ExternalAkkaHttpActor(settings.port,
      settings.interface, settings.httpsPort, settings.serverSettings))
  }
}

case class ExternalAkkaHttpActor(port: Int, interface: String, httpsPort: Option[Int],
                            settings: ServerSettings) extends AkkaHttpActor {
  val baseRoutes: Route =
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
        path("ping") {
          complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        }
    }

  override def routes: Route = if (ExternalAkkaHttpRouteContainer.isEmpty) {
    log.error("no routes defined")
    reject()
  } else {
    ExternalAkkaHttpRouteContainer.getRoutes.foldLeft(baseRoutes)(_ ~ _)
  }
}
