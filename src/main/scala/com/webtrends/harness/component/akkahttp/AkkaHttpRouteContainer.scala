package com.webtrends.harness.component.akkahttp

import java.util.Collections

import akka.http.scaladsl.server.Route

import scala.collection.JavaConversions._

object AkkaHttpRouteContainer {
  private val routes = Collections.synchronizedSet[Route](new util.HashSet[Route]())
  def isEmpty = routes.isEmpty
  def addRoute(r: Route) = routes.add(r)
  def getRoutes = routes.toSet
}

