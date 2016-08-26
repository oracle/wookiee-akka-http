/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import com.webtrends.harness.app.HActor
import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.health.HealthComponent

import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._


object AkkaHttpActor {
  def props = Props(classOf[AkkaHttpActor])
}

class AkkaHttpActor extends HActor {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  var binding: Option[Future[Http.ServerBinding]] = None

  def bind = {
    val route =
      path("ping") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>pong</h1>"))
        }
      }
    binding = Some(Http().bindAndHandle(route, "0.0.0.0", 7070))
  }

  def unbind = {
    binding.map(b => b.flatMap(_.unbind()))
  }

  override def receive = super.receive orElse {
    case "bind" => bind
    case StopComponent => unbind
  }

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = super.getHealth
}