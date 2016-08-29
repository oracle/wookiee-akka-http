/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.route

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.config.Config
import com.webtrends.harness.authentication.CIDRRules
import com.webtrends.harness.component.spray.directive.CIDRDirectives
import com.webtrends.harness.component.spray.route.RouteAccessibility.RouteAccessibility
import spray.routing._

import scala.collection.JavaConverters._
/**
 * @author Michael Cuthbert on 11/14/14.
 */
object RouteManager extends CIDRDirectives {
  var cidrRules: Option[CIDRRules] = None

  def apply(config: Config) = cidrRules = Some(CIDRRules(config))

  //Now using Java ConcurrentHashMap due to previous use of deprecated Scala "with SynchronizedMap"
  private val routes = new ConcurrentHashMap[RouteAccessibility, ConcurrentHashMap[String, Route]]()

  def addRoute(name: String, route: Route, routeAccess: Set[RouteAccessibility] = Set(RouteAccessibility.INTERNAL)) = {
    externalLogger.debug(s"new route registered with route manager [$name] and accessiblity of ${routeAccess.mkString(", ")}")

    routeAccess.foreach { access: RouteAccessibility =>
      routes.putIfAbsent(access, new ConcurrentHashMap())
      routes.get(access).put(name, route)
    }
  }

  def getRoute(service: String, access: RouteAccessibility = RouteAccessibility.INTERNAL): Option[Route] = {
    routes.containsKey(access) match {
      case true =>
        val m = routes.get(access)
        m.containsKey(service) match {
          case true => Some(m.get(service))
          case false => None
        }
      case false => None
    }
  }

  def getRoutes(access: RouteAccessibility = RouteAccessibility.INTERNAL): Iterable[Route] = {
    routes.containsKey(access) match {
      case true => routes.get(access).values.iterator().asScala.toIterable
      case false => Iterable[Route]()
    }
  }
}
