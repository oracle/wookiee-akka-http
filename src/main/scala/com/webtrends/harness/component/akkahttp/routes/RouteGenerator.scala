package com.webtrends.harness.component.akkahttp.routes

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes.{InternalServerError, NoContent, OK}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.corsRejectionHandler
import com.webtrends.harness.command.{Bean, ExecuteCommand, MapBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.{Auth, Params, QueryParams, RequestHeaders, TimeOfRequest, entityMarshaller, serialization}
import com.webtrends.harness.component.akkahttp.directives.AkkaHttpCORS
import com.webtrends.harness.component.akkahttp.methods.EndpointConfig
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.logging.Logger
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}

import scala.collection.{immutable, mutable}
import scala.util.{Failure, Success}

object RouteGenerator {
  private val defaultRouteFormats: Formats = DefaultFormats ++ JodaTimeSerializers.all

//  def makeRoute(commandRef: ActorRef, customMarshaller: endpoint: EndpointConfig)(implicit log: Logger, timeout: Timeout): Route =
//    commandInnerDirective(
//      commandRef,
//      parseRouteSegments(endpoint.path),
//      endpoint.path,
//      endpoint.method,
//      endpoint.defaultHeaders,
//      endpoint.enableCors)

  // TODO: marshaller/unmarshaller largely handled inside commandRef now, what's still needed?
  def makeRoute[T <: AnyRef : Manifest, U](
                                                     commandRef: ActorRef,
                                                     endpoint: EndpointConfig,
                                                     customMarshaller: U => Array[Byte],
                                                   )(implicit log: Logger, timeout: Timeout): Route = {
    val url = endpoint.path
    val httpPath = parseRouteSegments(url)
    val method = endpoint.method
    val defaultHeaders = endpoint.defaultHeaders
    val enableCors = endpoint.enableCors

    // TODO: first, figure out how you can get execute to return an AkkaHttpResponse
    // Then marshal the result using customMarshaller

    // TODO: then, work out where authorization plays into all of this

    // TODO: 
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

  protected[routes] def parseRouteSegments(path: String)(implicit log: Logger): Directive1[AkkaHttpPathSegments] = {
    val segs = path.split("/").filter(_.nonEmpty).toSeq
    var segCount = 0
    try {
      val dir = segs.tail.foldLeft(segs.head.asInstanceOf[Any]) { (x, y) =>
        y match {
          case s1: String if s1.startsWith("$") =>
            segCount += 1
            (x match {
              case pStr: String => if (pStr.startsWith("$")) {
                segCount += 1
                Segment / Segment
              } else pStr / Segment
              case pMatch: PathMatcher[Unit] if segCount == 1 => pMatch / Segment
              case pMatch: PathMatcher[Tuple1[String]] if segCount == 2 => pMatch / Segment
              case pMatch: PathMatcher[(String, String)] if segCount == 3 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String)] if segCount == 4 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String, String)] if segCount == 5 => pMatch / Segment
              case pMatch: PathMatcher[(String, String, String, String, String)] if segCount == 6 => pMatch / Segment

            }).asInstanceOf[PathMatcher[_]]
          case s1: String =>
            (x match {
              case pStr: String => if (pStr.startsWith("$")) {
                segCount += 1
                Segment / s1
              } else pStr / s1
              case pMatch: PathMatcher[_] => pMatch / s1
            }).asInstanceOf[PathMatcher[_]]
        }
      }

      // Create holders for any arguments on the query path
      segCount match {
        case 0 if segs.size == 1 => p(path) & provide(new AkkaHttpPathSegments {})
        case 0 => p(dir.asInstanceOf[PathMatcher[Unit]]) & provide(new AkkaHttpPathSegments {})
        case 1 => p(dir.asInstanceOf[PathMatcher[Tuple1[String]]]).as(Holder1)
        case 2 => p(dir.asInstanceOf[PathMatcher[(String, String)]]).as(Holder2)
        case 3 => p(dir.asInstanceOf[PathMatcher[(String, String, String)]]).as(Holder3)
        case 4 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String)]]).as(Holder4)
        case 5 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String)]]).as(Holder5)
        case 6 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String, String)]]).as(Holder6)
      }
    } catch {
      case ex: Throwable =>
        log.error(s"Error adding path ${path}", ex)
        throw ex
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
