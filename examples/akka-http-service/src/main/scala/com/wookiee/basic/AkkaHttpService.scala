package com.wookiee.basic

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.{HttpHeader, HttpMethods, RequestEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, completeWith}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.entityMarshaller
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpParameters, AkkaHttpRequest, AkkaHttpResponse, EndpointType, RouteGenerator}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service
import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.JodaTimeSerializers

import scala.collection.immutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  override def addCommands: Unit = {
    // Get endpoint
    addAkkaHttpEndpoint("getTest",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToHello,
      echo,
      responseHandler
    )

    // POST endpoint
    addAkkaHttpEndpoint("postTest",
      HttpMethods.POST,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToPayloadString,
      echo,
      responseHandler
    )
    // GET endpoint with segments
    addAkkaHttpEndpoint("getTest/$name",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo,
      responseHandler
    )

    addAkkaHttpEndpoint("account/$accountGuid/report/$reportId",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo,
      responseHandler
    )
  }

  def reqToHello(r: AkkaHttpRequest): Future[String] = Future.successful("Hello World")
  def reqToPayloadString(r: AkkaHttpRequest): Future[String] =
    RouteGenerator.entityToString(r.requestBody.get)
  def reqToRouteParams(r: AkkaHttpRequest): Future[AkkaHttpParameters] =
    Future.successful(r.params)

  def echo[T](x: T): Future[T] = {
    Future.successful(x)
  }

  def stringResponse(result: String): Route = {
    complete(StatusCodes.OK, result)
  }
}
