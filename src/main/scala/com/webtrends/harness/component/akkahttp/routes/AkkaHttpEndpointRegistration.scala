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

import akka.http.scaladsl.model.{HttpHeader, HttpMethod}
import akka.http.scaladsl.server.Route
import com.webtrends.harness.app.HActor
import com.webtrends.harness.command.CommandHelper
import com.webtrends.harness.component.akkahttp.AkkaHttpManager
import com.webtrends.harness.logging.{ActorLoggingAdapter, Logger}
import com.webtrends.harness.utils.ConfigUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object EndpointType extends Enumeration {
  type EndpointType = Value
  val INTERNAL, EXTERNAL, BOTH = Value
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
                                                               accessLogIdGetter: AkkaHttpRequest => String = _ => "-",
                                                               enableCors: Boolean = false,
                                                               defaultHeaders: Seq[HttpHeader] = Seq.empty[HttpHeader]
                                                              )(implicit ec: ExecutionContext): Unit = {

    val accessLogger =  if (accessLoggingEnabled) Some(accessLogIdGetter) else None
    addCommand(name, businessLogic).map { ref =>
        val route = RouteGenerator
          .makeHttpRoute(path, method, ref, requestHandler, responseHandler, errorHandler, accessLogger, enableCors, defaultHeaders)

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

