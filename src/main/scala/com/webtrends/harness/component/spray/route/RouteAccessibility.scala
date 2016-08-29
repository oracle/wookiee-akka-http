package com.webtrends.harness.component.spray.route

object RouteAccessibility extends Enumeration {
  type RouteAccessibility = Value
  val INTERNAL, EXTERNAL = Value
}

// By default all Spray Routes are internal. There is no need for InternalOnly trait

trait ExternalOnly {
  this: SprayRoutes =>
  override def routeAccess = Set(RouteAccessibility.EXTERNAL)
}

trait ExternalAndInternal {
  this: SprayRoutes =>
  override def routeAccess = Set(RouteAccessibility.EXTERNAL, RouteAccessibility.INTERNAL)
}