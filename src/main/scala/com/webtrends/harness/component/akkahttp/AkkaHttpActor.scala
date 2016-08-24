/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.{ComponentMessage, StopComponent}
import com.webtrends.harness.health.HealthComponent

import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._

import scala.util.{Failure, Success}


object AkkaHttpActor {
  def props = Props(classOf[AkkaHttpActor])
}

case class AkkaHttpBind()
case class AkkaHttpUnbind()
case class AkkaHttpReloadRoutes()

class AkkaHttpActor extends HActor {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  var binding: Option[Future[Http.ServerBinding]] = None

  def reloadRoutes = {
    binding match {
      case None =>
        bind
      case Some(b) =>
        b.flatMap(_.unbind()) onComplete {
          case Success(s) =>
            bind
          case Failure(f) =>
            log.error(s"Failed to unbind akka-http server: $f")
        }
    }
  }

  def bind: Unit = {
    val routes = AkkaHttpRouteManager.getRoutes.reduceLeft(_ ~ _)
    binding = Some(Http().bindAndHandle(routes, "0.0.0.0", 7070))
  }

  def unbind = {
    binding.map(b => b.flatMap(_.unbind()))
  }

  override def receive = super.receive orElse {
    case AkkaHttpBind => bind
    case AkkaHttpUnbind => unbind
    case AkkaHttpReloadRoutes => reloadRoutes
    case StopComponent => unbind
  }

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = super.getHealth
}