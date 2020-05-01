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

package com.wookiee.basic

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, EndpointType, RouteGenerator}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service
import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.JodaTimeSerializers

import scala.concurrent.Future
import scala.concurrent.duration._

case class Message(message: String)
case class NotAuthorized(message: String) extends Exception
case class Forbidden(message: String) extends Exception

class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout: Timeout = Timeout(2.seconds)
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  implicit val sys: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def addCommands: Unit = {
    // Get endpoint
    addAkkaHttpEndpoint[Message, Message](
      "simple.get",
      "getTest",
      HttpMethods.GET,
      EndpointType.INTERNAL,
      reqToHello,
      echo[Message],
      stringResponse,
      authorizationRejections,
      _ => "myId"
    )

    // POST endpoint
    addAkkaHttpEndpoint[Message, Message](
      "post.test",
      "postTest",
      HttpMethods.POST,
      EndpointType.INTERNAL,
      reqToPayloadMessage,
      echo[Message],
      stringResponse,
      authorizationRejections
    )

    // GET endpoint with segments
    addAkkaHttpEndpoint[List[String], List[String]](
      "segment.lookup",
      "getTest/$name",
      HttpMethods.GET,
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo[List[String]],
      paramsResponse,
      authorizationRejections
    )

    addAkkaHttpEndpoint[List[String], List[String]](
      "report.lookup",
      "account/$accountGuid/report/$reportId",
      HttpMethods.GET,
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo[List[String]],
      paramsResponse,
      authorizationRejections
    )

    addAkkaHttpEndpoint[List[String], List[String]](
      "report.lookup.forbidden",
      "report/$reportId",
      HttpMethods.GET,
      EndpointType.INTERNAL,
      rejectReport1Calls,
      echo[List[String]],
      paramsResponse,
      authorizationRejections
    )
  }

  def reqToHello(r: AkkaHttpRequest): Future[Message] = Future.successful(Message("Hello World"))
  def reqToPayloadMessage(r: AkkaHttpRequest): Future[Message] =
    RouteGenerator.entityToString(r.requestBody.get).map(Message)
  def reqToRouteParams(r: AkkaHttpRequest): Future[List[String]] = {
    Future.successful(r.segments)
  }
  def rejectReport1Calls(r: AkkaHttpRequest): Future[List[String]] = {
    val segments = r.segments
    if (segments.headOption.contains("1")) {
      Future.failed(Forbidden("Access to report 1 forbidden"))
    } else {
      Future.successful(r.segments)
    }
  }

  def echo[T](x: T): Future[T] = {
    Future.successful(x)
  }

  def stringResponse(result: Message): Route = {
    complete(StatusCodes.OK, result.message)
  }
  def paramsResponse(result: Seq[String]): Route = {
    complete(StatusCodes.OK, result.mkString(","))
  }
  def authorizationRejections: PartialFunction[Throwable, Route] = {
    case ex: NotAuthorized =>
      complete(StatusCodes.Unauthorized, ex.message)
    case ex: Forbidden =>
      complete(StatusCodes.Forbidden, ex.message)
  }
}
