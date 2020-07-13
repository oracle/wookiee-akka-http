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

package com.wookiee.basic.handlers

import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.webtrends.harness.component.akkahttp.routes.AkkaHttpRequest
import com.wookiee.basic.handlers.Objects.Message

import scala.concurrent.{ExecutionContext, Future}

object RequestParsers {
  def reqToHello(r: AkkaHttpRequest): Future[Message] = Future.successful(Message("Hello World"))

  def reqToPayloadMessage(r: AkkaHttpRequest)(implicit ec: ExecutionContext, mat: Materializer): Future[Message] =
    Unmarshaller.stringUnmarshaller(r.requestBody.get).map(Message)

  def reqToRouteParams(r: AkkaHttpRequest): Future[List[String]] = {
    Future.successful(r.segments)
  }
}