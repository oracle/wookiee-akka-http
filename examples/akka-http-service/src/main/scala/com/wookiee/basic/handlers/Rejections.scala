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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import com.webtrends.harness.component.akkahttp.routes.AkkaHttpRequest
import com.wookiee.basic.handlers.Objects.{Forbidden, NotAuthorized}

object Rejections {
  def authorizationRejections(request: AkkaHttpRequest): PartialFunction[Throwable, Route] = {
    case ex: NotAuthorized =>
      complete(StatusCodes.Unauthorized, ex.message)
    case ex: Forbidden =>
      complete(StatusCodes.Forbidden, ex.message)
  }
}
