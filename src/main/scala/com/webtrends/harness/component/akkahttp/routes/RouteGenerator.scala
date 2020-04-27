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

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.corsRejectionHandler
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.command.ExecuteCommand
import com.webtrends.harness.logging.Logger

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}


trait AkkaHttpParameters
trait AkkaHttpPathSegments
trait AkkaHttpAuth

// TODO: Get rid of these
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

case class AkkaHttpRequest(
                            path: String,
                          // List, not Seq as Seq is not <: Product and route params are common command inputs
                            segments: List[String],
                            method: HttpMethod,
                            requestHeaders: Map[String, String],
                            queryParams: Map[String, String],
                            time: Long,
                            requestBody: Option[RequestEntity] = None
                          )

object RouteGenerator {
  // TODO: Add new rejection/exceptionHandler to recover with as new parameter
  // TODO: Verify catch all 500 match works
  def makeHttpRoute[T <: Product : ClassTag, V](path: String,
                                                method: HttpMethod,
                                                defaultHeaders: Seq[HttpHeader],
                                                enableCors: Boolean,
                                                commandRef: ActorRef,
                                                requestHandler: AkkaHttpRequest => Future[T],
                                                responseHandler: V => Route,
                                                rejectionHandler: PartialFunction[Throwable, Route])
                                               (implicit ec: ExecutionContext, log: Logger, timeout: Timeout): Route = {

    val httpPath = parseRouteSegments(path)
    httpPath { segments: AkkaHttpPathSegments =>
      respondWithHeaders(defaultHeaders: _*) {
        corsSupport(method, enableCors) {
          httpMethod(method) {
            parameterMap { paramMap: Map[String, String] =>
              extractRequest { request =>
                val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
                val httpEntity = getPayload(method, request)
                val notABean = AkkaHttpRequest(path, paramHoldersToList(segments), method, reqHeaders, paramMap, System.currentTimeMillis(), httpEntity)
                // http request handlers should be built with authorization in mind.
                onComplete((for {
                  requestObjs <- requestHandler(notABean)
                  commandResult <- (commandRef ? ExecuteCommand("", requestObjs, timeout))
                } yield responseHandler(commandResult.asInstanceOf[V])).recover(rejectionHandler)) {
                  case Success(route: Route) =>
                    route
                  case Failure(ex: Throwable) =>
                    val firstClass = ex.getStackTrace.headOption.map(_.getClassName)
                      .getOrElse(ex.getClass.getSimpleName)
                    log.warn(s"Unhandled Error [$firstClass - '${ex.getMessage}'], update rejection handlers for path: ${path}", ex)
                    complete(StatusCodes.InternalServerError, "There was an internal server error.")
                }
              }
            }
          }
        }
      }
    }
  }

  // This is horrible, but Akka-Http wrote their types in such a way that we can't figure out a cleaner way around this.
  // PathMatchers currently tied to objects typed explicitly by the number of matches a path needs to accomplish.
  // Don't expose this, just use the following conversion method to change it to something actually useful.
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
      handleRejections(corsRejectionHandler) & CorsDirectives.cors(corsSettings(immutable.Seq(method)))
    } else {
      pass
    }
  }

  // TODO: Replace as soon as you can figure out a generic way to define variable length path matchers
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

  def httpMethod(method: HttpMethod): Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
    case HttpMethods.PATCH => patch
  }

  def corsSettings(allowedMethods: immutable.Seq[HttpMethod]): CorsSettings = CorsSettings.Default(
    CorsSettings.defaultSettings.allowGenericHttpRequests,
    CorsSettings.defaultSettings.allowCredentials,
    CorsSettings.defaultSettings.allowedOrigins,
    CorsSettings.defaultSettings.allowedHeaders,
    allowedMethods,
    CorsSettings.defaultSettings.exposedHeaders,
    CorsSettings.defaultSettings.maxAge
  )

  def entityToString(req: RequestEntity)(implicit ec: ExecutionContext, mat: Materializer): Future[String] =
    Unmarshaller.stringUnmarshaller(req)

  def entityToBytes(req: RequestEntity)(implicit ec: ExecutionContext, mat: Materializer): Future[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller(req)
}
