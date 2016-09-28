package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean}

import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T], responseType: String = "_") extends BaseCommandResponse[T]
class AkkaHttpCommandBean() extends CommandBean

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

trait AkkaHttpBase {
  this: Command =>

  def addRoute(r: Route) = AkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})

  protected def commandOuterDirective = handleRejections(RejectionHandler.default) {
    commandInnerDirective(new CommandBean)
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean) = {
    httpPath { segments: AkkaHttpPathSegments =>
      httpParams { params: AkkaHttpParameters =>
        httpAuth { auth: AkkaHttpAuth =>
          bean.addValue("segments", segments)
          bean.addValue("params", params)
          bean.addValue("auth", auth)
          onComplete(execute(Some(bean)).mapTo[AkkaHttpCommandResponse[T]]) {
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
  }

  addRoute(commandOuterDirective)
}
