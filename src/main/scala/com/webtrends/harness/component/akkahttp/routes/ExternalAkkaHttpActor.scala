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
      //.reduceLeft(_ ~ _)
  }

  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }
/*
  override def getHealth : Future[HealthComponent] = {
      getPing.mapAll {
        case Success(_) =>
          HealthComponent(self.path.toString, ComponentState.NORMAL, "Healthy")
        case Failure(_) =>
          HealthComponent(self.path.toString, ComponentState.CRITICAL, "Failed to ping server.")
      }
  }
*/
  def getPing : Future[Boolean] = {
    val response = requestAsString(HttpRequest(HttpMethods.GET, s"http://$interface:$port/ping"))

    response.map {
      case (response,body) if response.status == StatusCodes.Success =>
        body.startsWith("pong")
      case (response,body) =>
        log.error(s"Unexpected response from ping check with status ${response.status}: $body")
        false
    }
  }
}
