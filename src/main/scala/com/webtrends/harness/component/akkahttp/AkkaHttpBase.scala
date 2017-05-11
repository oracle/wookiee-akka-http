package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.util.ByteString
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats, Serialization, jackson}

import scala.collection.immutable
import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T],
                                      responseType: String = "_",
                                      marshaller: Option[ToResponseMarshaller[T]] = None) extends BaseCommandResponse[T]


case class AkkaHttpRejection(rejection: String)
case class AkkaHttpException[T](entity: T,
                                statusCode: StatusCode = InternalServerError,
                                headers: immutable.Seq[HttpHeader] = immutable.Seq.empty,
                                marshaller: Option[ToEntityMarshaller[T]] = None) extends Throwable

class AkkaHttpCommandBean() extends CommandBean

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth


trait AkkaHttpBase {
  this: BaseCommand =>

  def createRoutes(): Unit = addRoute(commandOuterDirective)
  def addRoute(r: Route): Unit = ExternalAkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})
  def httpMethod: Directive0 = get
  def beanDirective(bean: CommandBean): Directive1[CommandBean] = provide(bean)

  protected def commandOuterDirective = {
    commandInnerDirective(new CommandBean)
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](inputBean: CommandBean,
                                                              pathName: String = path,
                                                              method: Directive0 = httpMethod) = {
    httpPath { segments: AkkaHttpPathSegments =>
      method {
        httpParams { params: AkkaHttpParameters =>
          httpAuth { auth: AkkaHttpAuth =>
            beanDirective(inputBean) { outputBean =>
              handleRejections(AkkaHttpBase.rejectionHandler) {
                extractMethod { extMethod =>
                  outputBean.addValue(AkkaHttpBase.Path, pathName)
                  outputBean.addValue(AkkaHttpBase.Method, extMethod.name)
                  outputBean.addValue(AkkaHttpBase.Segments, segments)
                  outputBean.addValue(AkkaHttpBase.Params, params)
                  outputBean.addValue(AkkaHttpBase.Auth, auth)
                  onComplete(execute(Some(outputBean)).mapTo[BaseCommandResponse[T]]) {
                    case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _, _)) => route
                    case Success(AkkaHttpCommandResponse(Some(data), _, None)) =>
                      completeWith(AkkaHttpBase.marshaller[T]()) { completeFunc => completeFunc(data) }
                    case Success(AkkaHttpCommandResponse(Some(data), _, Some(marshaller))) =>
                      completeWith(marshaller) { completeFunc => completeFunc(data) }
                    case Success(AkkaHttpCommandResponse(Some(unknown), _, _)) =>
                      log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                      complete(InternalServerError)
                    case Success(AkkaHttpCommandResponse(None, _, _)) => complete(NoContent)
                    case Success(response: BaseCommandResponse[T]) => (response.data, response.responseType) match {
                      case (None, _) => complete(NoContent)
                      case (Some(data), _) =>
                        completeWith(AkkaHttpBase.marshaller[T]()) { completeFunc => completeFunc(data) }
                    }
                    case Success(unknownResponse) =>
                      log.error(s"Got unknown response $unknownResponse")
                      complete(InternalServerError)
                    case Failure(AkkaHttpException(msg, statusCode, headers, None)) =>
                      val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                        PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue(AkkaHttpBase.entityMarshaller[T]())
                      completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
                    case Failure(AkkaHttpException(msg, statusCode, headers, Some(marshaller))) =>
                      val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                        PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue(marshaller.asInstanceOf[ToEntityMarshaller[T]])
                      completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
                    case Failure(f) =>
                      log.error(s"Command failed with $f")
                      complete(InternalServerError)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  createRoutes()
}

object AkkaHttpBase {
  val Path = "path"
  val Segments = "segments"
  val Params = "params"
  val Auth = "auth"
  val Method = "method"

  val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  val serialization = jackson.Serialization

  val rejectionHandler: RejectionHandler = RejectionHandler
    .default
    .mapRejectionResponse {
      case res @ HttpResponse(s, _, HttpEntity.Strict(_, data), _) =>
        val json = serialization.write(AkkaHttpRejection(data.utf8String))(formats)
        res.copy(entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(json)))
      case res => res
    }

  def marshaller[T <: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToResponseMarshaller[T] = {
    de.heikoseeberger.akkahttpjson4s.Json4sSupport.json4sMarshaller[T](s, fmt)
  }

  def entityMarshaller[T <: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToEntityMarshaller[T] = {
    de.heikoseeberger.akkahttpjson4s.Json4sSupport.json4sMarshaller[T](s, fmt)
  }

  def unmarshaller[T](ev: Manifest[T], s: Serialization = serialization, fmt: Formats = formats)
  : FromRequestUnmarshaller[T] = {
    val m = de.heikoseeberger.akkahttpjson4s.Json4sSupport.json4sUnmarshaller(ev, s, fmt)
    Unmarshaller.messageUnmarshallerFromEntityUnmarshaller(m)
  }
}
