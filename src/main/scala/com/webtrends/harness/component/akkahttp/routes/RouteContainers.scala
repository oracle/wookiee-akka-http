package com.webtrends.harness.component.akkahttp.routes

import akka.http.scaladsl.server.Route

trait RouteContainers {
  private val routes = collection.mutable.LinkedHashSet[Route]()

  def addRoute(r: Route): Unit = {
    routes.synchronized {
      routes.add(r)
    }
  }

  def isEmpty = routes.isEmpty
  def getRoutes = routes.toList

  protected[akkahttp] def clearRoutes() = {
    routes.synchronized {
      routes.clear()
    }
  }
}

object InternalAkkaHttpRouteContainer extends RouteContainers
object ExternalAkkaHttpRouteContainer extends RouteContainers
object WebsocketAkkaHttpRouteContainer extends RouteContainers