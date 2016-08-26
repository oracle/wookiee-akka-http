package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.PathDirectives
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean}

import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T], responseType: String = "_")
  extends BaseCommandResponse[T]

trait AkkaHttpBase {
  this: Command =>

  def addRoute(r: Route) = AkkaHttpRouteContainer.addRoute(r)

  protected def commandDirective[T <: AnyRef : Manifest](bean: CommandBean = CommandBean(Map())) = {
    handleRejections(RejectionHandler.default) & extractRequestContext.flatMap { requestContext =>
      bean.addValue("akkaHttpReqCtx", requestContext)
      onComplete[BaseCommandResponse[T]](execute[T](Some(bean))
        .mapTo[BaseCommandResponse[T]])
        .map {
          case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _)) => route
          case Success(_) => failWith(new Exception("foo"))
          case Failure(f) => failWith(f)
        }
    }
  }
}

trait AkkaHttpGet extends AkkaHttpBase {
  this: Command =>

  addRoute(PathDirectives.path(path) {
    get {
      commandDirective().tapply(_._1)
    }
  })

}

