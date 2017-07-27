package com.webtrends.harness.component.akkahttp.routes

import java.util.Collections

import akka.http.scaladsl.server.Route

import scala.collection.JavaConversions._

object ExternalAkkaHttpRouteContainer {
  private val routes = Collections.synchronizedSet[Route](new java.util.LinkedHashSet[Route]())
  def isEmpty = routes.isEmpty
  def addRoute(r: Route) = routes.add(r)
  def getRoutes = routes.toSet
}

