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

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives.{extractRequest, ignoreTrailingSlash, _}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.stream.Materializer
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.CommandHelper
import com.webtrends.harness.component.akkahttp.AkkaHttpManager
import com.webtrends.harness.component.akkahttp.routes.EndpointType.EndpointType
import com.webtrends.harness.component.akkahttp.routes.RouteGenerator.{paramHoldersToList, parseRouteSegments, requestLocales}
import com.webtrends.harness.component.akkahttp.websocket.AkkaHttpWebsocket
import com.webtrends.harness.logging.{ActorLoggingAdapter, Logger, LoggingAdapter}
import com.webtrends.harness.utils.ConfigUtil

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object EndpointType extends Enumeration {
  type EndpointType = Value
  val INTERNAL, EXTERNAL, BOTH = Value
}

// These are all of the more optional bits of configuring an endpoint
case class EndpointOptions(
                           accessLogIdGetter: AkkaHttpRequest => String = _ => "-",
                           defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader],
                           corsSettings: Option[CorsSettings] = None,
                           routeTimerLabel: Option[String] = None,
                           requestHandlerTimerLabel: Option[String] = None,
                           businessLogicTimerLabel: Option[String] = None,
                           responseHandlerTimerLabel: Option[String] = None
                         )
object EndpointOptions {
  val default: EndpointOptions = EndpointOptions()
}

trait AkkaHttpEndpointRegistration {
  this: CommandHelper with ActorLoggingAdapter with HActor =>
  import AkkaHttpEndpointRegistration._
  private val accessLoggingEnabled = ConfigUtil.getDefaultValue(
    s"${AkkaHttpManager.ComponentName}.access-logging.enabled", config.getBoolean, true)
  if (accessLoggingEnabled) log.info("Access Logging Enabled") else log.info("Access Logging Disabled")
  implicit val logger: Logger = log

  def addAkkaHttpEndpoint[T <: Product: ClassTag, U: ClassTag](name: String,
                                                               path: String,
                                                               method: HttpMethod,
                                                               endpointType: EndpointType.EndpointType,
                                                               requestHandler: AkkaHttpRequest => Future[T],
                                                               businessLogic: T => Future[U],
                                                               responseHandler: U => Route,
                                                               errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                               options: EndpointOptions = EndpointOptions.default
                                                              )(implicit
                                                                ec: ExecutionContext,
                                                                responseTimeout: Option[FiniteDuration] = None,
                                                                timeoutHandler: Option[HttpRequest => HttpResponse] = None
                                                              ): Unit = {

    val sysTo = FiniteDuration(config.getDuration("akka.http.server.request-timeout").toNanos, TimeUnit.NANOSECONDS)
    val timeout = responseTimeout match {
      case Some(to) if to > sysTo =>
        log.warning(s"Time out of ${to.toMillis}ms for $method $path exceeds system max time out of ${sysTo.toMillis}ms.")
        sysTo
      case Some(to) => to
      case None => sysTo
    }

    addCommand(name, businessLogic).map { ref =>
        val route = RouteGenerator
          .makeHttpRoute(path, method, ref, requestHandler, responseHandler, errorHandler, timeout,
            timeoutHandler.getOrElse(defaultTimeoutResponse), options, accessLoggingEnabled)

        addRoute(endpointType, route)
    }
  }


  /**
   * Main method used to register a websocket. Has native support for compression based on the 'Accept-Encoding' header
   * provided on the request
   *
   * @param path Path to access this WS, can add query segments with '$', e.g. "/some/$param/in/path"
   * @param authHandler Handle auth here before request, if an exception is thrown it will bubble up to 'errorHandler'
   * @param inputHandler Meant to convert a TextMessage into our main 'T' input type, will be provided with the auth object from 'authHandler'
   * @param businessLogic Main business logic for this WS, takes input type 'T' and outputs type 'U'
   * @param responseHandler Logic to parse output type 'U' back to a TextMessage so it can be returned to client
   * @param onClose Logic to be called when a WS closes, good for resource cleanup, can be empty
   * @param errorHandler Handling logic in case an error is thrown through this process
   * @param options Options for this endpoint, not all fields are used in the WS case
   *
   * @tparam A Class that can be used to hold auth information in case downstream requires it
   * @tparam I The main input type we should expect, will want to create a parser from TextMessage to it in 'inputHandler'
   * @tparam O The main output type we should expect, will want to parse it back to a TextMessage in 'responseHandler'
   */
  def addAkkaHttpWebsocket[I: ClassTag, O <: Product : ClassTag, A <: Product : ClassTag](path: String,
                                                                                        authHandler: AkkaHttpRequest => Future[A],
                                                                                        inputHandler: (A, TextMessage) => Future[I],
                                                                                        businessLogic: I => Future[O],
                                                                                        responseHandler: O => TextMessage,
                                                                                        onClose: A => Unit,
                                                                                        errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                                                        options: EndpointOptions = EndpointOptions.default
                                                               )(implicit ec: ExecutionContext, mat: Materializer): Unit = {
    addAkkaWebsocketEndpoint(path, authHandler, inputHandler, businessLogic, responseHandler, onClose, errorHandler, options)
  }

  private def addRoute(endpointType: EndpointType, route: Route): Unit = {
    endpointType match {
      case EndpointType.INTERNAL =>
        InternalAkkaHttpRouteContainer.addRoute(route)
      case EndpointType.EXTERNAL =>
        ExternalAkkaHttpRouteContainer.addRoute(route)
      case EndpointType.BOTH =>
        ExternalAkkaHttpRouteContainer.addRoute(route)
        InternalAkkaHttpRouteContainer.addRoute(route)
    }
  }
}

object AkkaHttpEndpointRegistration extends LoggingAdapter {
  def defaultTimeoutResponse(request: HttpRequest): HttpResponse = {
    HttpResponse(
      StatusCodes.ServiceUnavailable,
      entity = HttpEntity(ContentTypes.`application/json`, s"""{"error": "${StatusCodes.ServiceUnavailable.defaultMessage}"}""")
    )
  }

  def addAkkaWebsocketEndpoint[I: ClassTag, O <: Product : ClassTag, A <: Product : ClassTag](
                                                     path: String,
                                                     authHandler: AkkaHttpRequest => Future[A],
                                                     inputHandler: (A, TextMessage) => Future[I],
                                                     businessLogic: I => Future[O],
                                                     responseHandler: O => TextMessage,
                                                     onClose: A => Unit,
                                                     errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                                                     options: EndpointOptions = EndpointOptions.default)
                                                     (implicit ec: ExecutionContext, mat: Materializer): Unit = {
    val httpPath = parseRouteSegments(path)(log)
    val route = ignoreTrailingSlash {
      httpPath { segments: AkkaHttpPathSegments =>
        extractRequest { request =>
          parameterMap { paramMap: Map[String, String] =>
            val reqHeaders = request.headers.map(h => h.name.toLowerCase -> h.value).toMap
            val locales = requestLocales(reqHeaders)
            val reqWrapper = AkkaHttpRequest(request.uri.path.toString, paramHoldersToList(segments), request.method, request.protocol,
              reqHeaders, paramMap, System.currentTimeMillis(), locales, None)

            handleExceptions(ExceptionHandler(errorHandler(reqWrapper))) {
              onSuccess(authHandler(reqWrapper)) { auth =>
                val ws = new AkkaHttpWebsocket(auth, inputHandler,
                  businessLogic, responseHandler, onClose, errorHandler, options)

                handleWebSocketMessages(ws.websocketHandler(reqWrapper))
              }
            }
          }
        }
      }
    }

    log.info(s"Adding Websocket on path $path to routes")
    WebsocketAkkaHttpRouteContainer.addRoute(route)
  }
}
