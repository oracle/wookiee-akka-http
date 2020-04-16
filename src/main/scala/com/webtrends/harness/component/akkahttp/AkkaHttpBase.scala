package com.webtrends.harness.component.akkahttp

import java.util.Map.Entry

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.HttpHeader.ParsingResult.{Error, Ok}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.{MethodDirectives, PathDirectives}
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.{ConfigObject, ConfigValue}
import com.webtrends.harness.app.Harness
import com.webtrends.harness.command.{Bean, Command, MapBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase._
import com.webtrends.harness.component.akkahttp.directives.AkkaHttpCORS
import com.webtrends.harness.component.akkahttp.logging.AccessLog
import com.webtrends.harness.component.akkahttp.methods.{CustomUnmarshallerEndpoint, DefaultJsonEndpoint, Endpoint}
import com.webtrends.harness.component.akkahttp.routes.ExternalAkkaHttpRouteContainer
import com.webtrends.harness.logging.LoggingAdapter
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats, Serialization, jackson}

import scala.collection.JavaConverters._
import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success, Try}

case class AkkaHttpCommandResponse[T](data: Option[T],
                                      marshaller: Option[ToResponseMarshaller[T]] = None,
                                      statusCode: Option[StatusCode] = None,
                                      headers: Seq[HttpHeader] = List())


case class AkkaHttpRejection(rejection: String)
case class AkkaHttpException[T](entity: T,
                                statusCode: StatusCode = InternalServerError,
                                headers: immutable.Seq[HttpHeader] = immutable.Seq.empty,
                                marshaller: Option[ToEntityMarshaller[T]] = None) extends Throwable(entity.toString)
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
case class Holder6(_1: String, _2: String, _3: String, _4: String, _5: String, _6: String)
  extends Product6[String, String, String, String, String, String] with AkkaHttpPathSegments

trait AkkaHttpBase extends  PathDirectives with MethodDirectives with AccessLog with LoggingAdapter {
  this: Command[MapBean, AkkaHttpCommandResponse[_]] =>

  def path:String = ""

  def endPoint:Endpoint = Endpoint(path, method)

  // The AkkaHttpCORS trait is provided to enable CORS if desired
  protected val corsEnabled: Boolean = false

  // Allows setting custom CORS settings by endpoint.
  // Default behavior allows all origins, allows credentials, and allows all methods defined in this Command for the path
  protected def corsSettingsByPath(path: String): CorsSettings = {
    AkkaHttpCORS.corsSettings(immutable.Seq(method))
  }

  val parseHeaders: Seq[HttpHeader] = {
    val defaultHeaderConfig: Iterable[ConfigObject] = Try {
      Harness.getActorSystem().get.settings.config.getObjectList("wookiee-akka-http.default-headers").asScala
    }.getOrElse(List())
    (for {
      header: ConfigObject <- defaultHeaderConfig
      entry: Entry[String, ConfigValue] <- header.entrySet().asScala
      parsedHeader = HttpHeader.parse(entry.getKey, entry.getValue.unwrapped().toString) match {
        case ParsingResult.Ok(header: HttpHeader, _) => header
        case ParsingResult.Error(error) =>
          throw new IllegalArgumentException(s"Error in configured header: ${error.summary}\nDetails:${error.detail}")
      }
    } yield parsedHeader).toSeq
  }

  def defaultHeaders: Seq[HttpHeader] = parseHeaders

  def createRoutes(): Unit = addRoute(commandOuterDirective)

  def addRoute(r: Route): Unit = ExternalAkkaHttpRouteContainer.addRoute(r)

  def httpPath: Directive1[AkkaHttpPathSegments] = p(path) & provide(new AkkaHttpPathSegments {})

  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})

  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})

  def method: HttpMethod = HttpMethods.GET

  def httpMethod(method: HttpMethod): Directive0 = AkkaHttpBase.httpMethod(method)

  def exceptionHandler[T<: AnyRef]: ExceptionHandler = ExceptionHandler {
    case AkkaHttpException(msg, statusCode, headers, marsh) =>
      val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
        fromStatusCodeAndHeadersAndValue(marsh.getOrElse(entityMarshaller[T](fmt = formats)))
      completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
    case ex: Throwable =>
      val firstClass = ex.getStackTrace.headOption.map(_.getClassName)
        .getOrElse(ex.getClass.getSimpleName)
      log.warn(s"Unhandled Error [$firstClass - '${ex.getMessage}'], Wrap in an AkkaHttpException before sending back", ex)
      complete(StatusCodes.InternalServerError, "There was an internal server error.")
  }

  def rejectionHandler: RejectionHandler = RejectionHandler
    .default
    .mapRejectionResponse {
      case res @ HttpResponse(_, _, HttpEntity.Strict(_, data), _) =>
        val json = serialization.write(AkkaHttpRejection(data.utf8String))(formats)
        res.copy(entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(json)))
      case res => res
    }

  def beanDirective(bean: MapBean, pathName: String = "", method: HttpMethod = HttpMethods.GET): Directive1[MapBean] = provide(bean)
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  private def corsSupport(path: String): Directive0 = {
    if (corsEnabled) {
      handleRejections(corsRejectionHandler) & CorsDirectives.cors(corsSettingsByPath(path))
    } else {
      pass
    }
  }

  protected def commandOuterDirective: Route = {
    commandInnerDirective()
  }

  protected def commandInnerDirective[T <: AnyRef : Manifest](url: String= endPoint.path,
                                                              method: HttpMethod = endPoint.method): Route = {

    httpPath { segments: AkkaHttpPathSegments =>
      respondWithHeaders(defaultHeaders: _*) {
        corsSupport(url) {
          httpMethod(method) {
            handleRejections(rejectionHandler) {
              handleExceptions(exceptionHandler[T]) {
                httpParams { params: AkkaHttpParameters =>
                  parameterMap { paramMap: Map[String, String] =>
                    httpAuth { auth: AkkaHttpAuth =>
                      extractRequest { request =>
                        val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
                        val inputBean = MapBean(mutable.Map((AkkaHttpBase.Path, url),
                          (AkkaHttpBase.Segments, segments), (AkkaHttpBase.Method, method),
                          (RequestHeaders, reqHeaders), (Params, params), (Auth, auth),
                          (TimeOfRequest, new java.lang.Long(System.currentTimeMillis())),
                          (QueryParams, paramMap)))
                        beanDirective(inputBean, url, method) { outputBean =>
                          onComplete(execute(outputBean)) {
                            case Success(akkaResp: AkkaHttpCommandResponse[T]) =>
                              val codeResp = akkaResp.copy(statusCode = Some(defaultCodes(outputBean, akkaResp)))
                              val finalResp = beforeReturn(outputBean, codeResp)
                              logAccess(request, outputBean, finalResp.statusCode)
                              respondWithHeaders(finalResp.headers: _*) {
                                finalResp match {
                                  case AkkaHttpCommandResponse(Some(route: StandardRoute), _, _, _) =>
                                    route
                                  case AkkaHttpCommandResponse(Some(data), None, sc, _) =>
                                    val succMarshaller: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                                      fromStatusCodeAndHeadersAndValue(entityMarshaller[T](fmt = formats))
                                    completeWith(succMarshaller) { completeFunc =>
                                      completeFunc((sc.get, immutable.Seq(), data))
                                    }
                                  case AkkaHttpCommandResponse(Some(data), Some(marshaller), _, _) =>
                                    completeWith(marshaller) { completeFunc => completeFunc(data) }
                                  case AkkaHttpCommandResponse(Some(unknown), _, sc, _) =>
                                    log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                                    complete(InternalServerError)
                                  case AkkaHttpCommandResponse(None, _, sc, _) =>
                                    complete(sc.get.asInstanceOf[StatusCode])
                                }
                              }
                            case Success(unknownResponse) =>
                              log.error(s"Got unknown response $unknownResponse")
                              logAccess(request, outputBean, Some(InternalServerError))
                              complete(InternalServerError)
                            case Failure(f) =>
                              val akkaEx = beforeFailedReturn[T](f match {
                                case akkaEx: AkkaHttpException[T] => akkaEx
                                case ex =>
                                  log.error(s"Command failed with $ex")
                                  AkkaHttpException[T](ex.getMessage.asInstanceOf[T], InternalServerError)
                              }) // Put all other errors into AkkaHttpException then call our beforeFailedReturn method
                              logAccess(request, outputBean, Some(akkaEx.statusCode))
                              akkaEx match {
                                case AkkaHttpException(msg, statusCode, headers, None) =>
                                  val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                                    fromStatusCodeAndHeadersAndValue(entityMarshaller[T](fmt = formats))
                                  completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
                                case AkkaHttpException(msg, statusCode, headers, Some(marshaller)) =>
                                  val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                                    fromStatusCodeAndHeadersAndValue(marshaller.asInstanceOf[ToEntityMarshaller[T]])
                                  completeWith(m) { completeFunc => completeFunc((statusCode, headers, msg.asInstanceOf[T])) }
                              } // If a marshaller provided, use it to transform the entity
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

    }

  }

  // Override to change default code behavior, defaults to OK with content and NoContent without
  def defaultCodes[T](commandBean: Bean, akkaResponse: AkkaHttpCommandResponse[T]): StatusCode = {
    if (akkaResponse.data.isEmpty)
      akkaResponse.statusCode.getOrElse(NoContent)
    else
      akkaResponse.statusCode.getOrElse(OK)
  }

  // Override to transform response or execute other code right before we return the response
  // Don't set statusCode to None
  def beforeReturn[T](commandBean: Bean, akkaResponse: AkkaHttpCommandResponse[T]):
  AkkaHttpCommandResponse[T] = {
    akkaResponse
  }

  // Override to transform exception response or execute other code right before we return the exception response
  // Don't set statusCode to None
  def beforeFailedReturn[T]( akkaException: AkkaHttpException[T]):
  AkkaHttpException[T] = {
    akkaException
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
  val RequestHeaders = "requestHeaders"
  val TimeOfRequest = "timeOfRequest"
  val PathParams = "pathParams"
  val KeyEntity = "Request-Entity"
  val KeyPath = "Selected-Path"

  val AHMetricsPrefix = "wookiee.akka-http"

  val MetricsPrefix = "wookiee.akka-http"

  val formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  val serialization = jackson.Serialization

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
    case HttpMethods.PATCH => patch
  }

  // Returns a new CommandBean that has been stripped of all Wookiee Akka Http dependent params,
  // good for when one is going to be sending the bean to services that don't have a
  // wookiee-akka-http dependency
  def beanClean(bean: MapBean): MapBean = {
    val blackList = List(Segments, Params, Auth, Method)
    val cleanMap = bean.map.filterKeys(k => !blackList.contains(k))
    MapBean(mutable.Map().++(cleanMap))
  }

  def parseHeader(name: String, value: String): Option[HttpHeader] = {
    HttpHeader.parse(name, value) match {
      case Ok(header, _) => Some(header)
      case Error(e) =>
        Harness.externalLogger.warn("Error parsing header: " + e.summary + "\nDetail: " + e.detail)
        None
    }
  }

  def marshaller[T<: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToResponseMarshaller[T] = {
    Json4sSupport.marshaller[T](s, fmt)
  }

  def entityMarshaller[T<: AnyRef](s: Serialization = serialization, fmt: Formats = formats): ToEntityMarshaller[T] = {
    Json4sSupport.marshaller[T](s, fmt)
  }

  def unmarshaller[T](ev: Manifest[T], s: Serialization = serialization, fmt: Formats = formats)
  : FromRequestUnmarshaller[T] = {
    val m = Json4sSupport.unmarshaller[T](ev, s, fmt)
    Unmarshaller.messageUnmarshallerFromEntityUnmarshaller(m)
  }
}
