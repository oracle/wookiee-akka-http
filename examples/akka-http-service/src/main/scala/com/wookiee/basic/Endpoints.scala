package com.wookiee.basic

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpRequest, EndpointConfig, EndpointType, WookieeEndpointConfig}
import com.wookiee.basic.handlers.Logic.echo
import com.wookiee.basic.handlers.Objects.Message
import com.wookiee.basic.handlers.Rejections.authorizationRejections
import com.wookiee.basic.handlers.RequestParsers.{reqToHello, reqToPayloadMessage}
import com.wookiee.basic.handlers.Responses.stringResponse
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import javax.ws.rs.{GET, POST, Path}
import io.swagger.v3.oas.annotations.{ExternalDocumentation, OpenAPIDefinition, Operation, Parameter}
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.info.{Contact, Info}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.servers.{Server, ServerVariable}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Path("")
class Endpoints(executionContext: ExecutionContext, materializer: Materializer) {
  private implicit val ec = executionContext
  private implicit val mat = materializer

  @GET
  @Path("/accountGet")
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
  val getEndpoint = WookieeEndpointConfig[Message, Message](
    "simple.get",
    "getTest",
    HttpMethods.GET,
    EndpointType.INTERNAL,
    reqToHello _,
    echo _,
    stringResponse _,
    authorizationRejections _,
    _=> "myId")

  @POST
  @Path("/accountPost")
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
  )
  val postEndpoint = WookieeEndpointConfig[Message, Message](
    "post.test",
    "postTest",
    HttpMethods.POST,
    EndpointType.INTERNAL,
    reqToPayloadMessage _,
    echo[Message] _,
    stringResponse _,
    authorizationRejections _
  )
}

case class SimpleGetEndpoint(
                              name: String = "simple.get",
                              path: String = "getTest",
                              method: HttpMethod = HttpMethods.GET,
                              endpointType: EndpointType.EndpointType = EndpointType.INTERNAL,
                              requestHandler: AkkaHttpRequest => Future[Message] = reqToHello,
                              businessLogic: Message => Future[Message] = echo,
                              responseHandler: Message => Route = stringResponse,
                              errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route] = authorizationRejections
                            ) extends EndpointConfig[Message, Message] {
  @GET
  @Path("/account2")
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

//case class SimplePostEndpoint(
//                                    name: String = "post.test",
//                                    path: String = "postTest",
//                                    method: HttpMethod = HttpMethods.POST,
//                                    endpointType: EndpointType.EndpointType = EndpointType.BOTH,
//                                    requestHandler: AkkaHttpRequest => Future[Message] = reqToPayloadMessage,
//                                    businessLogic: Message => Future[Message] = echo,
//                                    responseHandler: Message => Route = stringResponse,
//                                    rejectionHandler: AkkaHttpRequest => PartialFunction[Throwable, Route] = authorizationRejections
//                                  ) extends EndpointConfig[Message, Message]
