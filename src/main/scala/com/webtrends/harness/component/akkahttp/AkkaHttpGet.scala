package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.StatusCodes._
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
          case Success(AkkaHttpCommandResponse(Some(route: Route), _)) => StandardRoute(route)
          case Success(unknownResponse) =>
            log.error(s"Got unknown response $unknownResponse")
            complete(InternalServerError)
          case Failure(f) =>
            log.error(s"Command failed with $f")
            complete(InternalServerError)
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

trait AkkaHttpPost extends AkkaHttpBase {
  this: Command =>

  addRoute(PathDirectives.path(path) {
    post {
      commandDirective().tapply(_._1)
    }
  })

}
