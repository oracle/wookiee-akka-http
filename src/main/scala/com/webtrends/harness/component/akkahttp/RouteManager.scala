package com.webtrends.harness.component.spray.route

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.Config
import com.webtrends.harness.authentication.CIDRRules
import com.webtrends.harness.component.spray.directive.CIDRDirectives
import com.webtrends.harness.component.spray.route.RouteAccessibility.RouteAccessibility
import spray.routing._

import scala.collection.JavaConverters._

object RouteManager {

  //Now using Java ConcurrentHashMap due to previous use of deprecated Scala "with SynchronizedMap"
  private val routes = new ConcurrentHashMap[RouteAccessibility, ConcurrentHashMap[String, Route]]()

  def addRoute() = {
    externalLogger.debug(s"new route registered with route manager [$name] and accessiblity of ${routeAccess.mkString(", ")}")

    routeAccess.foreach { access: RouteAccessibility =>
      routes.putIfAbsent(access, new ConcurrentHashMap())
      routes.get(access).put(name, route)
    }
  }

  def getRoutes(access: RouteAccessibility = RouteAccessibility.INTERNAL): Iterable[Route] = {
    routes.containsKey(access) match {
      case true => routes.get(access).values.iterator().asScala.toIterable
      case false => Iterable[Route]()
    }
  }
}
