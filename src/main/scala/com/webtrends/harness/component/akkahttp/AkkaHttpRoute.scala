package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Route

trait AkkaHttpRoute {
  def route: Route
  AkkaHttpRouteContainer.addRoute(route)
}
