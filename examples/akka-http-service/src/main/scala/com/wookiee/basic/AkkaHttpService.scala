package com.wookiee.basic

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers.fromStatusCodeAndHeadersAndValue
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.{HttpHeader, HttpMethods, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, completeWith}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.entityMarshaller
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, AkkaHttpResponse, EndpointType}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service

import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.JodaTimeSerializers

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all

  override def addCommands: Unit = {
    }

  def responseHandler: PartialFunction[Any, Route] =  {
    case (resp: AkkaHttpResponse[AkkaHttpRequest]) =>
      val succMarshaller: ToResponseMarshaller[(StatusCode, immutable.Seq[HttpHeader], Option[AkkaHttpRequest])] =
        fromStatusCodeAndHeadersAndValue(entityMarshaller[Option[AkkaHttpRequest]](fmt = formats))
      completeWith(succMarshaller) { completeFunc =>
        completeFunc((resp.statusCode.get, immutable.Seq(), resp.data))
      }
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
