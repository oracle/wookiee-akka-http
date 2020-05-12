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
import akka.http.scaladsl.model.HttpMethods
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, EndpointType}
import com.webtrends.harness.service.Service
import com.wookiee.basic.handlers.Auth.rejectReport1Calls
import com.wookiee.basic.handlers.Logic.echo
import com.wookiee.basic.handlers.Objects.Message
import com.wookiee.basic.handlers.RequestParsers.{reqToHello, reqToPayloadMessage, reqToRouteParams}
import com.wookiee.basic.handlers.Rejections.authorizationRejections
import com.wookiee.basic.handlers.Responses.{paramsResponse, stringResponse}
import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.JodaTimeSerializers

import scala.concurrent.duration._

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
}
