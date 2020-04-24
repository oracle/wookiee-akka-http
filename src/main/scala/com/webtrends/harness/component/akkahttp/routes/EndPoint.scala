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
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

sealed trait Endpoint {
  val path: String
  val method: HttpMethod
}


object Endpoint {
  // Use for endpoints with no request entity
  def apply(path: String, method: HttpMethod) = BareEndpoint(path, method)

  // Generic end pointing supporting application/json which will deserialize to the specified class using the class's formats
  def apply(path: String, method: HttpMethod, entityClass: Class[_]) = DefaultJsonEndpoint(path, method, entityClass)

  // End point with a provided custom marshaller
  def apply[T <: AnyRef](path: String, method: HttpMethod, unmarshaller: FromRequestUnmarshaller[T]) = CustomUnmarshallerEndpoint(path, method, unmarshaller)

}

case class BareEndpoint(path: String, method: HttpMethod) extends Endpoint
case class DefaultJsonEndpoint(path: String, method: HttpMethod, entityClass: Class[_]) extends Endpoint
case class CustomUnmarshallerEndpoint[T <: AnyRef](path: String, method: HttpMethod, unmarshaller: FromRequestUnmarshaller[T]) extends Endpoint
case class EndpointConfig(path: String, method: HttpMethod, defaultHeaders: Seq[HttpHeader], enableCors: Boolean) extends Endpoint
