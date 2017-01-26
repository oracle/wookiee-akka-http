package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, InternalAkkaHttpRouteContainer}

trait AkkaHttpInternal extends AkkaHttpBase {
  this: BaseCommand =>

  override def addRoute(r: Route): Unit = InternalAkkaHttpRouteContainer.addRoute(r)
}
