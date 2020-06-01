/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.component.akkahttp.routes

import java.util.Locale
import java.util.Locale.LanguageRange

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.RouteResult.{Complete, Rejected}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.util.Timeout
import ch.megard.akka.http.cors.javadsl
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.ExecuteCommand
import com.webtrends.harness.component.akkahttp.logging.AccessLog
import com.webtrends.harness.logging.Logger

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scala.collection.JavaConverters._


trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

case class AkkaHttpRequest(
                            path: String,
                          // List, not Seq as Seq is not <: Product and route params are common command inputs
                            segments: List[String],
                            method: HttpMethod,
                            protocol: HttpProtocol,
                            requestHeaders: Map[String, String],
                            queryParams: Map[String, String],
                            time: Long,
                            locales: List[Locale],
                            requestBody: Option[RequestEntity] = None
                          )

object RouteGenerator {
  def makeHttpRoute[T <: Product : ClassTag, V](path: String,
                                                method: HttpMethod,
                                                commandRef: ActorRef,
                                                requestHandler: AkkaHttpRequest => Future[T],
                                                responseHandler: V => Route,
                                                errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                accessLogIdGetter: Option[AkkaHttpRequest => String] = None,
                                                defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader],
                                                corsSettings: Option[CorsSettings] = None)
                                               (implicit ec: ExecutionContext, log: Logger, timeout: Timeout): Route = {

    val httpPath = parseRouteSegments(path)
    httpPath { segments: AkkaHttpPathSegments =>
      respondWithHeaders(defaultHeaders: _*) {
        httpMethod(method) {
          parameterMap { paramMap: Map[String, String] =>
            extractRequest { request =>
              val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
              val httpEntity = getPayload(method, request)
              val locales = requestLocales(reqHeaders)
              val reqWrapper = AkkaHttpRequest(path, paramHoldersToList(segments), request.method, request.protocol,
                reqHeaders, paramMap, System.currentTimeMillis(), locales, httpEntity)
              corsSupport(method, corsSettings, reqWrapper, accessLogIdGetter) {
                // http request handlers should be built with authorization in mind.
                onComplete((for {
                  requestObjs <- requestHandler(reqWrapper)
                  commandResult <- (commandRef ? ExecuteCommand("", requestObjs, timeout))
                } yield responseHandler(commandResult.asInstanceOf[V])).recover(errorHandler(reqWrapper))) {
                  case Success(route: Route) =>
                    mapRouteResult {
                      case Complete(response) =>
                        accessLogIdGetter.foreach(g => AccessLog.logAccess(reqWrapper, g(reqWrapper), response.status))
                        Complete(response)
                      case Rejected(rejections) =>
                        // TODO: Current expectation is that user's errorHandler should already handle rejections before this point
                        ???
                    }(route)
                  case Failure(ex: Throwable) =>
                    val firstClass = ex.getStackTrace.headOption.map(_.getClassName)
                      .getOrElse(ex.getClass.getSimpleName)
                    log.warn(s"Unhandled Error [$firstClass - '${ex.getMessage}'], update rejection handlers for path: ${path}", ex)
                    accessLogIdGetter.foreach(g => AccessLog.logAccess(reqWrapper, g(reqWrapper), StatusCodes.InternalServerError))
                    complete(StatusCodes.InternalServerError, "There was an internal server error.")
                }
              }
            }
          }
        }
      }
    }
  }

  // TODO: #129
  // Use these to generically extract values from a query string
  private case class Holder1(_1: String) extends Product1[String] with AkkaHttpPathSegments
  private case class Holder2(_1: String, _2: String) extends Product2[String, String] with AkkaHttpPathSegments
  private case class Holder3(_1: String, _2: String, _3: String) extends Product3[String, String, String] with AkkaHttpPathSegments
  private case class Holder4(_1: String, _2: String, _3: String, _4: String)
    extends Product4[String, String, String, String] with AkkaHttpPathSegments
  private case class Holder5(_1: String, _2: String, _3: String, _4: String, _5: String)
    extends Product5[String, String, String, String, String] with AkkaHttpPathSegments
  private case class Holder6(_1: String, _2: String, _3: String, _4: String, _5: String, _6: String)
    extends Product6[String, String, String, String, String, String] with AkkaHttpPathSegments

  // TODO: #129
  // Don't expose this, just use the paramHoldersToList to change it to something actually useful.
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

  // TODO: #129
  private def paramHoldersToList(segments: AkkaHttpPathSegments): List[String] =
    segments match {
      case Holder1(a) => List(a)
      case Holder2(a, b) => List(a, b)
      case Holder3(a, b, c) => List(a, b, c)
      case Holder4(a, b, c, d) => List(a, b, c, d)
      case Holder5(a, b, c, d, e) => List(a, b, c, d, e)
      case Holder6(a, b, c, d, e, f) => List(a, b, c, d, e, f)
      case _ => List()
    }

  def getPayload(method: HttpMethod, request:HttpRequest):Option[RequestEntity] = method match {
    case HttpMethods.PUT | HttpMethods.POST => Some(request.entity)
    case _ => None
  }

  private def corsSupport(method: HttpMethod,
                          corsSettings: Option[CorsSettings],
                          request: AkkaHttpRequest,
                          accessLogIdGetter:Option[AkkaHttpRequest => String]): Directive0 =
    corsSettings match {
      case Some(cors) => handleRejections(corsRejectionHandler(request, accessLogIdGetter)) &
        CorsDirectives.cors(cors.withAllowedMethods(immutable.Seq(method)))
      case None => pass
    }

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
    case HttpMethods.PATCH => patch
  }

  def requestLocales(headers: Map[String, String]): List[Locale] =
     headers.get("accept-language") match {
      case Some(localeString) if localeString.nonEmpty => LanguageRange.parse(localeString).asScala
        .map(language => Locale.forLanguageTag(language.getRange)).toList
      case _ => Nil
    }

  private def corsRejectionHandler(request: AkkaHttpRequest, accessLogIdGetter:Option[AkkaHttpRequest => String]): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handleAll[javadsl.CorsRejection] { rejections =>
        val causes = rejections.map(_.cause.description).mkString(", ")
        accessLogIdGetter.foreach(g => AccessLog.logAccess(request, g(request), StatusCodes.Forbidden))
        complete((StatusCodes.Forbidden, s"CORS: $causes"))
      }
      .result()

}
