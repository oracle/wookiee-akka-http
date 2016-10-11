package com.webtrends.harness.component.akkahttp

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.StopComponent

import scala.util.{Failure, Success}

object ExternalAkkaHttpActor {
  def props(settings: ExternalAkkaHttpSettings) = {
    Props(classOf[ExternalAkkaHttpActor], settings.port, settings.interface, settings.serverSettings)
  }
}

class ExternalAkkaHttpActor(port: Int, interface: String, settings: ServerSettings) extends HActor {

  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  val serverSource = Http().bind(interface, port, settings = settings)

  val bindingFuture = serverSource
    .to(Sink.foreach { conn => conn.handleWith(RouteResult.route2HandlerFlow(routes)) })
    .run()

  bindingFuture.onComplete {
    case Success(s) =>
      log.info(s"akka-http external-server bound to port $port on interface $interface")
    case Failure(f) =>
      log.error(s"Failed to bind akka-http external-server: $f")
  }

  def unbind = bindingFuture.flatMap(_.unbind())

  def routes = if (AkkaHttpRouteContainer.isEmpty) {
    log.error("not routes defined")
    failWith(new Exception("no routes defined"))
  } else {
    AkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)
  }

  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }
}
