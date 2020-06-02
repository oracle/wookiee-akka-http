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
import io.swagger.v3.oas.annotations.{ExternalDocumentation, OpenAPIDefinition, Operation, Parameter}
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.info.{Contact, Info}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.servers.{Server, ServerVariable}
import javax.ws.rs.{GET, POST, Path}

import scala.concurrent.duration._

@Path("")
@OpenAPIDefinition(
  info = new Info(
    title = "Infinity Customer Account Management API",
    version = "1.0",
    contact = new Contact(
      url = "http://oracle.com",
      name = "John",
      email = "John@oracle.com"
    )
  ),
  tags = Array(
    new Tag(
      name = "Tag 1",
      description = "Tag 1 description",
      externalDocs = new ExternalDocumentation(
        description = "Doc desc",
        url = "http://oracle.com"
      )
    ),
    new Tag(
      name = "Tag 2",
      description = "Tag 2 description",
      externalDocs = new ExternalDocumentation(
        description = "Doc 2 desc",
        url = "http://oracle.com"
      )
    )
  ),
  externalDocs = new ExternalDocumentation(
    description = "Definition doc desc",
    url = "http://oracle.com"
  ),
  security = Array(
    new SecurityRequirement(
      name = "Requirement 1",
      scopes = Array("scope 1", "scope2")
    ),
    new SecurityRequirement(
      name = "Requirement 2",
      scopes = Array("scope 2", "scope3")
    )
  ),
  servers = Array(
    new Server(
      description = "Server 1",
      url = "http://foo",
      variables = Array(
        new ServerVariable(
          name = "var 1",
          description = "var 1 description",
          defaultValue = "1",
          allowableValues = Array("1","2")
        ),
        new ServerVariable(
          name = "var 2",
          description = "var 2 description",
          defaultValue = "1",
          allowableValues = Array("1","2")
        )
      )
    )
  )
)
class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout: Timeout = Timeout(2.seconds)
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  implicit val sys: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val endpoints = new Endpoints(materializer.executionContext, materializer)

  override def addCommands: Unit = {
//    addAkkaHttpEndpoint(endpoints.getEndpoint)
//    addAkkaHttpEndpoint(endpoints.postEndpoint)
//    addAkkaHttpEndpoint(SimpleGetEndpoint())
    @Path("/inlinedoc")
    case class e0() {
      @GET
      @Operation(
        summary = "Get accounts",
        description = "Get list of all accounts",
        tags = Array("Account Management"),
        responses = Array(
          new ApiResponse(
            responseCode = "200",
            description = "Account response",
            content = Array(
              new Content(
                mediaType = "application/json",
                schema = new Schema(
                  implementation = classOf[String]
                )
              )
            )
          ),
          new ApiResponse(
            responseCode = "500",
            description = "Internal server error"
          )
        )
      )
      def docs() = {}
    }
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

    @Path("/accountPost")
    case class FooEndpointDocs() {
      @POST
      @Operation(
        summary = "Create foo",
        description = "Create an foo",
        tags = Array("Account Management"),
        parameters = Array(
          new Parameter(
            name = "account_name",
            in = ParameterIn.PATH,
            description = "account name",
            required = true,
            schema = new Schema(
              implementation = classOf[String],
              example = "Oracle Corp"
            )
          ),
          new Parameter(
            name = "account_guid",
            in = ParameterIn.PATH,
            description = "account guid",
            required = false,
            schema = new Schema(
              implementation = classOf[String],
              example = "xcthunliq"
            )
          ),
          new Parameter(
            name = "account_status",
            in = ParameterIn.PATH,
            description = "account status",
            required = true,
            schema = new Schema(
              implementation = classOf[Int],
              allowableValues = Array("1","2","3"),
              defaultValue = "1",
              example = "1"
            )
          )
        ),
        responses = Array(
          new ApiResponse(
            responseCode = "200",
            description = "Account response",
            content = Array(
              new Content(
                mediaType = "application/json",
                schema = new Schema(
                  implementation = classOf[String]
                )
              )
            )
          ),
          new ApiResponse(
            responseCode = "500",
            description = "Internal server error"
          )
        )
      ) def docs() = {}
    }
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
//
//    addAkkaHttpEndpoint[List[String], List[String]](
//      "report.lookup",
//      "account/$accountGuid/report/$reportId",
//      HttpMethods.GET,
//      EndpointType.INTERNAL,
//      reqToRouteParams,
//      echo[List[String]],
//      paramsResponse,
//      authorizationRejections
//    )
//
//    addAkkaHttpEndpoint[List[String], List[String]](
//      "report.lookup.forbidden",
//      "report/$reportId",
//      HttpMethods.GET,
//      EndpointType.INTERNAL,
//      rejectReport1Calls,
//      echo[List[String]],
//      paramsResponse,
//      authorizationRejections
//    )
  }
}