package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.{Command, MapBean}
import com.webtrends.harness.component.akkahttp.routes.InternalAkkaHttpRouteContainer
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}

trait AkkaHttpInternal extends AkkaHttpBase {
  this: Command[MapBean, AkkaHttpCommandResponse[_]] =>

  override def addRoute(r: Route): Unit = InternalAkkaHttpRouteContainer.addRoute(r)
}
