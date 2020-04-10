package com.webtrends.harness.component.akkahttp.routes

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NoContent, OK}
import akka.http.scaladsl.server.Directives.{complete, completeWith, extractRequest, handleExceptions, handleRejections, onComplete, parameterMap, pass, respondWithHeaders}
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.{Directive0, Directive1, ExceptionHandler, RejectionHandler, Route, StandardRoute}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.corsRejectionHandler
import com.webtrends.harness.command.{Bean, ExecuteCommand, MapBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.{Auth, Params, QueryParams, RequestHeaders, TimeOfRequest, entityMarshaller, serialization}
import com.webtrends.harness.component.akkahttp.directives.AkkaHttpCORS
import com.webtrends.harness.component.akkahttp.methods.EndpointConfig
import com.webtrends.harness.component.akkahttp.{AkkaHttpAuth, AkkaHttpBase, AkkaHttpCommandResponse, AkkaHttpException, AkkaHttpParameters, AkkaHttpPathSegments, AkkaHttpRejection}
import com.webtrends.harness.logging.Logger
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}

import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success}

object RouteGenerator {
  private val defaultRouteFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  def makeRoute(commandRef: ActorRef, endpoint: EndpointConfig)(implicit log: Logger, timeout: Timeout): Route =
    commandInnerDirective(
      commandRef,
      parseRouteSegments(endpoint.path),
      endpoint.path,
      endpoint.method,
      endpoint.defaultHeaders,
      endpoint.enableCors)

  protected[routes] def parseRouteSegments(path: String): Directive1[AkkaHttpPathSegments] = {
    ??? // Waiting on Krishna's implementation
  }

  // TODO: marshaller/unmarshaller largely handled inside commandRef now, what's still needed?
  protected[routes] def commandInnerDirective[T <: AnyRef : Manifest](
                                                     commandRef: ActorRef,
                                                     httpPath: Directive1[AkkaHttpPathSegments],
                                                     url: String,
                                                     method: HttpMethod,
                                                     defaultHeaders: Seq[HttpHeader],
                                                     enableCors: Boolean
                                                   )(implicit log: Logger, timeout: Timeout): Route = {

    httpPath { segments: AkkaHttpPathSegments =>
      respondWithHeaders(defaultHeaders: _*) {
        corsSupport(method, enableCors) {
          AkkaHttpBase.httpMethod(method) {
            handleRejections(rejectionHandler(defaultRouteFormats)) { // Todo: part of addHttpCommand, also localization
              handleExceptions(exceptionHandler[T](defaultRouteFormats)) { // TODO: part of addHttpCommand, also localization
                httpParams { params: AkkaHttpParameters =>
                  parameterMap { paramMap: Map[String, String] =>
                    httpAuth { auth: AkkaHttpAuth =>
                      extractRequest { request =>
                        val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
                        val inputBean = formatBean(url, segments, method, reqHeaders, params, auth, paramMap)
                        beanDirective(inputBean, url, method) { outputBean =>
                          onComplete(commandRef ? ExecuteCommand("", outputBean, timeout)) {
                            case Success(akkaResp: AkkaHttpCommandResponse[T]) =>
                              val codeResp = akkaResp.copy(statusCode = Some(defaultCodes(outputBean, akkaResp)))
                              logAccess(request, outputBean, codeResp.statusCode)
                              respondWithHeaders(codeResp.headers: _*) {
                                codeResp match {
                                  case AkkaHttpCommandResponse(Some(route: StandardRoute), _, _, _) =>
                                    route
                                  case AkkaHttpCommandResponse(Some(data), None, sc, _) =>
                                    val succMarshaller: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                                      fromStatusCodeAndHeadersAndValue(entityMarshaller[T](fmt = defaultRouteFormats)) // TODO: Passed in
                                    completeWith(succMarshaller) { completeFunc =>
                                      completeFunc((sc.get, immutable.Seq(), data))
                                    }
                                  case AkkaHttpCommandResponse(Some(data), Some(marshaller), _, _) =>
                                    completeWith(marshaller) { completeFunc => completeFunc(data) }
                                  case AkkaHttpCommandResponse(Some(unknown), _, sc, _) =>
                                    log.error(s"Got unknown data from AkkaHttpCommandResponse $unknown")
                                    complete(InternalServerError)
                                  case AkkaHttpCommandResponse(None, _, sc, _) =>
                                    complete(sc.get)
                                }
                              }
                            case Success(unknownResponse) =>
                              log.error(s"Got unknown response $unknownResponse")
                              logAccess(request, outputBean, Some(InternalServerError))
                              complete(InternalServerError)
                            case Failure(f) =>
                              val akkaEx = f match {
                                case akkaEx: AkkaHttpException[T] => akkaEx
                                case ex =>
                                  log.error(s"Command failed with $ex")
                                  AkkaHttpException[T](ex.getMessage.asInstanceOf[T], InternalServerError)
                              } // Put all other errors into AkkaHttpException then call our beforeFailedReturn method
                              logAccess(request, outputBean, Some(akkaEx.statusCode))
                              akkaEx match {
                                case AkkaHttpException(msg, statusCode, headers, None) =>
                                  val m: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], T)] =
                                    fromStatusCodeAndHeadersAndValue(entityMarshaller[T](fmt = defaultRouteFormats)) // TODO: Passed in
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

  private def corsSupport(method: HttpMethod, enableCors: Boolean): Directive0 = {
    if (enableCors) {
      handleRejections(corsRejectionHandler) & CorsDirectives.cors(AkkaHttpCORS.corsSettings(immutable.Seq(method)))
    } else {
      pass
    }
  }

  // TODO: should this be one of the methods passed into addHttpCommand?
  // Yes, yes it should.
  def rejectionHandler(formats: Formats): RejectionHandler = RejectionHandler
    .default
    .mapRejectionResponse {
      case res @ HttpResponse(_, _, HttpEntity.Strict(_, data), _) =>
        val json = serialization.write(AkkaHttpRejection(data.utf8String))(formats)
        res.copy(entity = HttpEntity.Strict(ContentTypes.`application/json`, ByteString(json)))
      case res => res
    }

  // TODO: plug in custom marshallers
  def exceptionHandler[T<: AnyRef](formats: Formats)(implicit log: Logger): ExceptionHandler = ExceptionHandler {
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

  // TODO: is this method actually overriden anywhere? Is it needed?
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})

  // TODO: is this method actually overriden anywhere? Is it needed?
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})

  //TODO: This gets overridden by different connection types, potentially make part of endpointConfig
  def beanDirective(bean: MapBean, pathName: String = "", method: HttpMethod = HttpMethods.GET): Directive1[MapBean] =
    provide(bean)

  // TODO: Find ouy where this is actually overridden
  // Override to change default code behavior, defaults to OK with content and NoContent without
  def defaultCodes[T](commandBean: Bean, akkaResponse: AkkaHttpCommandResponse[T]): StatusCode = {
    if (akkaResponse.data.isEmpty)
      akkaResponse.statusCode.getOrElse(NoContent)
    else
      akkaResponse.statusCode.getOrElse(OK)
  }

  def logAccess(request: HttpRequest, bean: MapBean, statusCode: Option[StatusCode]): Unit = {
    // TODO: Here's probably best case for currying some state in this object's methods
  }

  protected[routes] def formatBean(url: String,
                                   segments: AkkaHttpPathSegments,
                                   method: HttpMethod,
                                   reqHeaders: Map[String, String],
                                   params: AkkaHttpParameters,
                                   auth: AkkaHttpAuth,
                                   paramMap: Map[String, String]) =
    MapBean(mutable.Map((AkkaHttpBase.Path, url),
      (AkkaHttpBase.Segments, segments), (AkkaHttpBase.Method, method),
      (RequestHeaders, reqHeaders), (Params, params), (Auth, auth),
      (TimeOfRequest, new java.lang.Long(System.currentTimeMillis())),
      (QueryParams, paramMap))
}
