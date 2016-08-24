package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import akka.http.scaladsl.server.directives.PathDirectives
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean}

import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T], responseType: String = "_")
  extends BaseCommandResponse[T]

trait AkkaHttpBaseRoute{
  this: Command =>

  protected val akkaHttpManager =  context.actorSelection(HarnessConstants.ComponentFullName + "/" + "wookiee-akkahttp")

  protected def addRoute(route: Route) {
    AkkaHttpRouteManager.addRoute(route)
    akkaHttpManager ! AkkaHttpReloadRoutes
  }
}

trait AkkaHttpGet extends AkkaHttpBaseRoute {
  this: Command =>

  addRoute(PathDirectives.path(path){get{executeRoute()}})

  protected def executeRoute[T<:AnyRef:Manifest](bean:Option[CommandBean]=None) = {
    onComplete[BaseCommandResponse[T]](execute[T](None).mapTo[BaseCommandResponse[T]]) {
      case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _)) => route
      case Success(_) => failWith(new Exception("foo"))
      case Failure(f) => failWith(f)
    }
  }
}

