/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.component.spray

import _root_.spray.can.server.ServerSettings
import _root_.spray.http.{HttpRequest, HttpResponse, StatusCodes}
import _root_.spray.httpx.Json4sSupport
import _root_.spray.routing.directives.LogEntry
import _root_.spray.routing.{HttpServiceActor, Rejected, Route}
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.event.Logging
import akka.http.javadsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.settings.ServerSettings
import com.webtrends.harness.authentication.CIDRRules
import com.webtrends.harness.component._
import com.webtrends.harness.component.spray.directive.CIDRDirectives
import com.webtrends.harness.component.spray.route.{RouteAccessibility, RouteManager}
import com.webtrends.harness.component.spray.serialization.EnumerationSerializer
import com.webtrends.harness.health.{ComponentState, _}
import com.webtrends.harness.logging.ActorLoggingAdapter
import org.json4s.ext.JodaTimeSerializers

import scala.concurrent.ExecutionContext

@SerialVersionUID(1L) case class HttpStartProcessing()
@SerialVersionUID(1L) case class HttpReloadRoutes()


class CoreSprayWorker extends Http
    with ActorLoggingAdapter
    with CIDRDirectives
    with Json4sSupport
    with ComponentHelper {
  implicit val json4sFormats = org.json4s.DefaultFormats + new EnumerationSerializer(ComponentState) ++ JodaTimeSerializers.all

  val spSettings = ServerSettings(context.system)
  Http().bindAndHandle(baseRoutes, "0.0.0.0", Materilizer())
  var cidrRules: Option[CIDRRules] = Some(CIDRRules(context.system.settings.config))

  def baseRoutes(echo: ActorRef)(implicit mat: ActorFlowMaterializer, ec: ExecutionContext) = {
    unmatchedPath { remainingPath =>
      complete(StatusCodes.NotFound, "The requested resource could not be found.")
    }
  }

  def receive: Receive = initializing

  /**
    * Establish our routes and other receive handlers
    */
  def initializing: Receive = {
    case HttpStartProcessing =>
      context.become(running)
    case HttpReloadRoutes => // Do nothing
  }

  def running: Receive = runRoute(logRequestResponse(myLog _) {
    getRoutes
  }) orElse {
    case HttpReloadRoutes =>
      context.become(initializing)
      self ! HttpStartProcessing
  }

  /**
    * Fetch routes from all registered services and concatenate with our default ones that
    * are defined below.
    */
  def getRoutes: Route = {
    val serviceRoutes = RouteManager.getRoutes(RouteAccessibility.EXTERNAL).filter(r => !r.equals(Map.empty))
    (serviceRoutes ++ List(this.baseRoutes)).reduceLeft(_ ~ _)
  }

  def myLog(request: HttpRequest): Any => Option[LogEntry] = {
    case x: HttpResponse =>
      createLogEntry(request, s"${x.status}")
    case Rejected(rejections) =>
      createLogEntry(request, s"Rejection ${rejections.toString()}")
    case x =>
      createLogEntry(request, x.toString())
  }

  def createLogEntry(request: HttpRequest, text: String): Some[LogEntry] = {
    Some(LogEntry(s"#### ${request.method} ${request.uri}  => $text", Logging.DebugLevel))
  }
}
