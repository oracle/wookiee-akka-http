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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpMethod, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.CommandHelper
import com.webtrends.harness.component.akkahttp.AkkaHttpManager
import com.webtrends.harness.logging.{ActorLoggingAdapter, Logger}
import com.webtrends.harness.utils.ConfigUtil
import AkkaHttpEndpointRegistration._

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
}

object AkkaHttpEndpointRegistration {
  def defaultTimeoutResponse(request: HttpRequest): HttpResponse = {
    HttpResponse(
      StatusCodes.ServiceUnavailable,
      entity = HttpEntity(ContentTypes.`application/json`, s"""{"error": "${StatusCodes.ServiceUnavailable.defaultMessage}"}""")
    )
  }
}
