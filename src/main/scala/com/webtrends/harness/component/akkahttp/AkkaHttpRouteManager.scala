package com.webtrends.harness.component.akkahttp

import java.util
import java.util.Collections

import akka.http.scaladsl.server.Route
import scala.collection.JavaConversions._

object AkkaHttpRouteManager {
  private val routes = Collections.synchronizedList[Route](new util.ArrayList[Route]())
  def addRoute(r: Route) = {
    println("########## adding route")
    routes.add(r)
  }
  def getRoutes = routes.toList
}

