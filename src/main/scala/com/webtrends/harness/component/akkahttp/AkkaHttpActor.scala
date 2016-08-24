/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, HttpRequest, ContentTypes, HttpEntity}
import akka.http.scaladsl.server.RouteResult
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.{ComponentMessage, StopComponent}
import com.webtrends.harness.health.HealthComponent

import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._

import scala.util.{Failure, Success}


object AkkaHttpActor {
  def props = Props(classOf[AkkaHttpActor])
}

case class AkkaHttpUnbind()

class AkkaHttpActor extends HActor {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  def routes = AkkaHttpRouteManager.getRoutes.reduceLeft(_ ~ _)

  val serverSource = Http().bind(interface = "0.0.0.0", port =7070)

  val bindingFuture = serverSource.to(Sink.foreach{conn =>
    conn.handleWith(RouteResult.route2HandlerFlow(routes))
  }).run()

  var reloadingInProgress = true


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