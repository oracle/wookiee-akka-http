package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpHeader, StatusCode}
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, jackson}

import scala.collection.immutable
import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T],
                                      responseType: String = "_",
                                      marshaller: Option[ToResponseMarshaller[T]] = None) extends BaseCommandResponse[T]


case class AkkaHttpException[T](entity: T,
                                statusCode: StatusCode = InternalServerError,
                                headers: immutable.Seq[HttpHeader] = immutable.Seq.empty,
                                marshaller: Option[ToEntityMarshaller[T]] = None) extends Throwable

class AkkaHttpCommandBean() extends CommandBean

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpEntity
trait AkkaHttpAuth


trait AkkaHttpBase {
  this: BaseCommand =>


  def addRoute(r: Route): Unit = AkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})

  protected def commandOuterDirective = {
    commandInnerDirective(new CommandBean)
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean) = {
    httpPath { segments: AkkaHttpPathSegments =>
      httpParams { params: AkkaHttpParameters =>
        httpAuth { auth: AkkaHttpAuth =>
          handleRejections(RejectionHandler.default) {
            bean.addValue(AkkaHttpBase.Segments, segments)
            bean.addValue(AkkaHttpBase.Params, params)
            bean.addValue(AkkaHttpBase.Auth, auth)
            onComplete(execute(Some(bean)).mapTo[BaseCommandResponse[T]]) {
              case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _, _)) => route
              case Success(AkkaHttpCommandResponse(Some(route: Route), _, _)) => StandardRoute(route)
              case Success(AkkaHttpCommandResponse(Some(data), _, None)) =>
                completeWith(AkkaHttpBase.marshaller[T]) { completeFunc => completeFunc(data)}
              case Success(AkkaHttpCommandResponse(Some(data), _, Some(marshaller))) =>
                completeWith(marshaller) { completeFunc => completeFunc(data)}
              case Success(AkkaHttpCommandResponse(Some(unknown), _, _)) =>
                log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                complete(InternalServerError)
              case Success(AkkaHttpCommandResponse(None, _, _)) => complete(NoContent)
              case Success(response: BaseCommandResponse[T]) => (response.data, response.responseType) match {
                case (None, _) => complete(NoContent)
                case (Some(data), _) =>
                  completeWith(AkkaHttpBase.marshaller[T]) { completeFunc => completeFunc(data)}
              }
              case Success(unknownResponse) =>
                log.error(s"Got unknown response $unknownResponse")
                complete(InternalServerError)
              case Failure(AkkaHttpException(msg, statusCode, headers, None)) =>
                val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                  PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue(AkkaHttpBase.entityMarshaller[T])
                completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T]))}
              case Failure(AkkaHttpException(msg, statusCode, headers, Some(marshaller))) =>
                val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                  PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue(marshaller.asInstanceOf[ToEntityMarshaller[T]])
                completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T]))}
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

  val formats = DefaultFormats ++ JodaTimeSerializers.all

  def marshaller[T <: AnyRef]: ToResponseMarshaller[T] = {
    de.heikoseeberger.akkahttpjson4s.Json4sSupport.json4sMarshaller[T](jackson.Serialization, formats)
  }

  def entityMarshaller[T <: AnyRef]: ToEntityMarshaller[T] = {
    de.heikoseeberger.akkahttpjson4s.Json4sSupport.json4sMarshaller[T](jackson.Serialization, formats)
  }
}
