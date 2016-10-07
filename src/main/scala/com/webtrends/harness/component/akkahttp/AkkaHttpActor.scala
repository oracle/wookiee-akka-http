/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.health.HealthComponent

import scala.concurrent.Future
import scala.util.{Failure, Success}


object AkkaHttpActor {
  def props(settings: ExternalAkkaHttpSettings) = {
    Props(classOf[AkkaHttpActor], settings.port, settings.interface, settings.serverSettings)
  }
}

case class AkkaHttpUnbind()

class AkkaHttpActor(port: Int, interface: String, settings: ServerSettings) extends HActor {
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


  def routes = AkkaHttpRouteContainer.getRoutes.reduceLeft(_ ~ _)

  def unbind = {
    bindingFuture.flatMap(_.unbind())
  }

  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = super.getHealth
}