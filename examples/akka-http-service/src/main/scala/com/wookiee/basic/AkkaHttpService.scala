package com.wookiee.basic

import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, AkkaHttpResponse, EndpointType}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service

import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)

  override def addCommands: Unit = {
    }

  def responseHandler: PartialFunction[Any, Route] =  {
    case(resp:AkkaHttpResponse[AkkaHttpRequest]) => complete(resp.toString)
  }

  // Get endpoint
  addAkkaHttpEndpoint("getTest",
    HttpMethods.GET,
    false,
    Seq(),
    EndpointType.INTERNAL,
    (req:AkkaHttpRequest)=> Right(req),
    (req:AkkaHttpRequest) => Future.successful(AkkaHttpResponse(Some("Hello World"), Some(StatusCodes.OK))),
    responseHandler
  )

 // POST endpoint
  addAkkaHttpEndpoint("postTest",
    HttpMethods.POST,
    false,
    Seq(),
    EndpointType.INTERNAL,
    (req:AkkaHttpRequest)=> Right(req),
    (req:AkkaHttpRequest) => Future.successful(AkkaHttpResponse(Some(req), Some(StatusCodes.OK))),
    responseHandler
  )
  // GET endpoint with segments
  addAkkaHttpEndpoint("getTest/$name",
    HttpMethods.GET,
    false,
    Seq(),
    EndpointType.INTERNAL,
    (req:AkkaHttpRequest)=> Right(req),
    (req:AkkaHttpRequest) => Future.successful(AkkaHttpResponse(Some(req), Some(StatusCodes.OK))),
    responseHandler
  )

  addAkkaHttpEndpoint("account/$accountGuid/report/$reportId",
    HttpMethods.GET,
    false,
    Seq(),
    EndpointType.INTERNAL,
    (req:AkkaHttpRequest)=> Right(req),
    (req:AkkaHttpRequest) => Future.successful(AkkaHttpResponse(Some(req), Some(StatusCodes.OK))),
    responseHandler
  )

}
