package com.webtrends.harness.component.akkahttp.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.pattern.ask
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.corsRejectionHandler
import com.webtrends.harness.command.{ExecuteCommand, MapBean}
import com.webtrends.harness.component.akkahttp.directives.AkkaHttpCORS
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.logging.Logger

import scala.collection.{immutable, mutable}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

case class AkkaHttpResponse[T](data: Option[T], statusCode: Option[StatusCode], headers: Seq[HttpHeader] = List())
case class AkkaHttpRequest(
                            path: String,
                            segments: AkkaHttpPathSegments,
                            method: HttpMethod,
                            requestHeaders: Map[String, String],
                            params: AkkaHttpParameters,
                            auth: AkkaHttpAuth,
                            queryParams: Map[String, String],
                            time: Long,
                            requestBody: Option[RequestEntity] = None
                          )

// TODO: potential state for frequent default handlers esp: regarding rejection/exception handlers
object RouteGenerator {

  // TODO: implement simple endpoint using as is, verify happy path
  // TODO: Identify common pattern for 204s, most likely just a business logic that returns Unit, that is handled by consumer's responseHandler
  // TODO: Probably move towards EitherT, or just revert to using Trys on the handlers. Feels weird having Either for requestHandler
  // and Try for the businessLogic directive
  // TODO: verify what it looks like to handle a 403 unauthorized exception that can occur from either request handler OR the businessLogic method
  // TODO: Add a default 500 exception handler to run if the consumer's responseHandler does not apply to result of either requesthandler or businessLogic
  def makeRoute[T <: Product : ClassTag, V](path: String,
                      method: HttpMethod,
                      defaultHeaders: Seq[HttpHeader],
                      enableCors: Boolean,
                      commandRef: ActorRef,
                      requestHandler: AkkaHttpRequest => Either[Throwable, T],
                      responseHandler: PartialFunction[Any, Route])(implicit log: Logger, timeout: Timeout): Route = {

    val httpPath = parseRouteSegments(path)
    httpPath { segments: AkkaHttpPathSegments =>
      respondWithHeaders(defaultHeaders: _*) {
        corsSupport(method, enableCors) {
          AkkaHttpBase.httpMethod(method) {
            // TODO: handleRejections and handleExceptions more part of marshaller, still here or only in marshaller now?
            httpParams { params: AkkaHttpParameters =>
              parameterMap { paramMap: Map[String, String] =>
                httpAuth { auth: AkkaHttpAuth =>
                  extractRequest { request =>
                    val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
                    val httpEntity =  getPayload(method, request)
                    val notABean = AkkaHttpRequest(path, segments, method, reqHeaders, params, auth, paramMap, System.currentTimeMillis(), httpEntity)
                    // http request handlers should be built with authorization in mind.
                    requestHandler(notABean) match {
                      case Right(requestInfo) =>
                        onComplete(commandRef ? ExecuteCommand("", requestInfo, timeout)) {
                          // TODO: non-happy path
                          case Success(resp: V) =>
                            responseHandler(resp)
                          case Failure(f: V) =>
                            log.info("business logic failed")
                            complete("response failed")
                        }
                      case Left(ex) =>
                        log.info("request failed")
                        complete("request failed")
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

  def getPayload(method: HttpMethod, request:HttpRequest):Option[RequestEntity] = method match {
    case HttpMethods.PUT | HttpMethods.POST => Some(request.entity)
    case _ => None
  }

  private def corsSupport(method: HttpMethod, enableCors: Boolean): Directive0 = {
    if (enableCors) {
      handleRejections(corsRejectionHandler) & CorsDirectives.cors(AkkaHttpCORS.corsSettings(immutable.Seq(method)))
    } else {
      pass
    }
  }

  // TODO: is this method actually overriden anywhere? Is it needed?
  def httpParams: Directive1[AkkaHttpParameters] = provide(new AkkaHttpParameters {})

  // TODO: is this method actually overriden anywhere? Is it needed?
  def httpAuth: Directive1[AkkaHttpAuth] = provide(new AkkaHttpAuth {})
}
