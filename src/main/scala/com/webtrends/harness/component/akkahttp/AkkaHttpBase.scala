package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{MethodDirectives, PathDirectives}
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.util.ByteString
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.routes.ExternalAkkaHttpRouteContainer
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats, Serialization, jackson}

import scala.collection.immutable
import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse[T](data: Option[T],
                                      responseType: String = "_",
                                      marshaller: Option[ToResponseMarshaller[T]] = None,
                                      statusCode: Option[StatusCode] = None) extends BaseCommandResponse[T]


case class AkkaHttpRejection(rejection: String)
case class AkkaHttpException[T](entity: T,
                                statusCode: StatusCode = InternalServerError,
                                headers: immutable.Seq[HttpHeader] = immutable.Seq.empty,
                                marshaller: Option[ToEntityMarshaller[T]] = None) extends Throwable(entity.toString)

class AkkaHttpCommandBean() extends CommandBean

trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

// Use these to generically extract values from a query string
case class Holder1(_1: String) extends Product1[String] with AkkaHttpPathSegments
case class Holder2(_1: String, _2: String) extends Product2[String, String] with AkkaHttpPathSegments
case class Holder3(_1: String, _2: String, _3: String) extends Product3[String, String, String] with AkkaHttpPathSegments
case class Holder4(_1: String, _2: String, _3: String, _4: String)
  extends Product4[String, String, String, String] with AkkaHttpPathSegments
case class Holder5(_1: String, _2: String, _3: String, _4: String, _5: String)
  extends Product5[String, String, String, String, String] with AkkaHttpPathSegments

trait AkkaHttpBase extends PathDirectives with MethodDirectives {
  this: BaseCommand =>

  def createRoutes(): Unit = addRoute(commandOuterDirective)
  def addRoute(r: Route): Unit = ExternalAkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})
  def method: HttpMethod = HttpMethods.GET
  def httpMethod(method: HttpMethod): Directive0 = AkkaHttpBase.httpMethod(method)
  def exceptionHandler[T <: AnyRef : Manifest]: ExceptionHandler = ExceptionHandler {
    case AkkaHttpException(msg, statusCode, headers, Some(marshaller)) =>
      val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
        fromStatusCodeAndHeadersAndValue(AkkaHttpBase.entityMarshaller[T](fmt = formats))
      completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
  }
  def beanDirective(bean: CommandBean, pathName: String = "", method: HttpMethod = HttpMethods.GET): Directive1[CommandBean] = provide(bean)

  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  protected def commandOuterDirective = {
    commandInnerDirective(new CommandBean)
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](inputBean: CommandBean,
                                                              url: String = path,
                                                              method: HttpMethod = method) = {
    httpPath { segments: AkkaHttpPathSegments =>
      httpMethod(method) {
        handleRejections(AkkaHttpBase.rejectionHandler) {
          handleExceptions(exceptionHandler[T]) {
            httpParams { params: AkkaHttpParameters =>
              parameterMap { paramMap: Map[String, String] =>
                httpAuth { auth: AkkaHttpAuth =>
                  extractMethod { extMethod =>
                    inputBean.addValue(AkkaHttpBase.Path, url)
                    inputBean.addValue(AkkaHttpBase.Method, method)
                    inputBean.addValue(AkkaHttpBase.Segments, segments)
                    // Query params that can be marshalled to a case class via httpParams
                    inputBean.addValue(AkkaHttpBase.Params, params)
                    inputBean.addValue(AkkaHttpBase.Auth, auth)
                    // Generic string Map of query params
                    inputBean.addValue(AkkaHttpBase.QueryParams, paramMap)
                    beanDirective(inputBean, url, method) { outputBean =>
                      onComplete(execute(Some(outputBean)).mapTo[BaseCommandResponse[T]]) {
                        case Success(AkkaHttpCommandResponse(Some(route: StandardRoute), _, _, _)) =>
                          route
                        case Success(AkkaHttpCommandResponse(Some(data), _, None, sc)) =>
                          val succMarshaller: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                            fromStatusCodeAndHeadersAndValue(AkkaHttpBase.entityMarshaller[T](fmt = formats))
                          completeWith(succMarshaller) { completeFunc =>
                            completeFunc((sc.getOrElse(OK), immutable.Seq[HttpHeader](), data))
                          }
                        case Success(AkkaHttpCommandResponse(Some(data), _, Some(marshaller), _)) =>
                          completeWith(marshaller) { completeFunc => completeFunc(data) }
                        case Success(AkkaHttpCommandResponse(Some(unknown), _, _, sc)) =>
                          log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                          complete(InternalServerError)
                        case Success(AkkaHttpCommandResponse(None, _, _, sc)) =>
                          complete(sc.getOrElse(NoContent).asInstanceOf[StatusCode])
                        case Success(response: BaseCommandResponse[T]) => (response.data, response.responseType) match {
                          case (None, _) => complete(NoContent)
                          case (Some(data), _) =>
                            completeWith(AkkaHttpBase.marshaller[T](fmt = formats)) { completeFunc => completeFunc(data) }
                        }
                        case Success(unknownResponse) =>
                          log.error(s"Got unknown response $unknownResponse")
                          complete(InternalServerError)
                        case Failure(AkkaHttpException(msg, statusCode, headers, None)) =>
                          val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                            fromStatusCodeAndHeadersAndValue(AkkaHttpBase.entityMarshaller[T](fmt = formats))
                          completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
                        case Failure(AkkaHttpException(msg, statusCode, headers, Some(marshaller))) =>
                          val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                            fromStatusCodeAndHeadersAndValue(marshaller.asInstanceOf[ToEntityMarshaller[T]])
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
  val QueryParams = "queryParams"

  val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  val serialization = jackson.Serialization

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
  }

  def rejectionHandler: RejectionHandler = RejectionHandler
    .default
    .mapRejectionResponse {
      case res @ HttpResponse(s, _, HttpEntity.Strict(_, data), _) =>
        val json = serialization.write(AkkaHttpRejection(data.utf8String))(formats)
        res.copy(entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(json)))
      case res => res
    }

  def marshaller[T <: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToResponseMarshaller[T] = {
    Json4sSupport.marshaller[T](s, fmt)
  }

  def entityMarshaller[T <: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToEntityMarshaller[T] = {
    Json4sSupport.marshaller[T](s, fmt)
  }

  def unmarshaller[T](ev: Manifest[T], s: Serialization = serialization, fmt: Formats = formats)
  : FromRequestUnmarshaller[T] = {
    val m = Json4sSupport.unmarshaller[T](ev, s, fmt)
    Unmarshaller.messageUnmarshallerFromEntityUnmarshaller(m)
  }
}
