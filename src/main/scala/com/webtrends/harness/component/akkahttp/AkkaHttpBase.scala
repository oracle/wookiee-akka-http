package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, jackson}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._

import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T], responseType: String = "_") extends BaseCommandResponse[T]
class AkkaHttpCommandBean() extends CommandBean

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpEntity
trait AkkaHttpAuth

trait AkkaHttpBase {
  this: Command =>

  implicit val serialization = jackson.Serialization
  implicit val formats       = DefaultFormats ++ JodaTimeSerializers.all

  def addRoute(r: Route) = AkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})

  protected def commandOuterDirective = {
    commandInnerDirective(new CommandBean)
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean) = {
    handleRejections(RejectionHandler.default) {
      httpPath { segments: AkkaHttpPathSegments =>
        httpParams { params: AkkaHttpParameters =>
          httpAuth { auth: AkkaHttpAuth =>
            bean.addValue(AkkaHttpBase.Segments, segments)
            bean.addValue(AkkaHttpBase.Params, params)
            bean.addValue(AkkaHttpBase.Auth, auth)
            onComplete(execute(Some(bean)).mapTo[BaseCommandResponse[T]]) {
              case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _)) => route
              case Success(AkkaHttpCommandResponse(Some(route: Route), _)) => StandardRoute(route)
              case Success(AkkaHttpCommandResponse(Some(unknown), _)) =>
                log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                complete(InternalServerError)
              case Success(AkkaHttpCommandResponse(None, _)) => complete(NoContent)
              case Success(response: BaseCommandResponse[T]) => (response.data, response.responseType) match {
                case (None, _) => complete(NoContent)
                case (Some(data), _) => complete(data)
              }
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
  }

  addRoute(commandOuterDirective)
}

object AkkaHttpBase {
  val Segments = "segments"
  val Params = "params"
  val Auth = "auth"
  val Entity = "entity"
}
