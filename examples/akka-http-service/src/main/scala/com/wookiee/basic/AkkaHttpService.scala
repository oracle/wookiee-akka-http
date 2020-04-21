package com.wookiee.basic

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpParameters, AkkaHttpPathSegments, AkkaHttpRequest, EndpointType, Holder1, Holder2, RouteGenerator}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.service.Service
import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.JodaTimeSerializers

import scala.concurrent.Future
import scala.concurrent.duration._

class AkkaHttpService extends Service with AkkaHttpEndpointRegistration {
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)
  def formats: Formats = DefaultFormats ++ JodaTimeSerializers.all
  implicit val sys: ActorSystem = context.system
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  case class Message(message: String)

  override def addCommands: Unit = {
    // Get endpoint
    addAkkaHttpEndpoint[Message, Message]("getTest",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToHello,
      echo[Message],
      stringResponse
    )

    // POST endpoint
    addAkkaHttpEndpoint[Message, Message]("postTest",
      HttpMethods.POST,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToPayloadMessage,
      echo[Message],
      stringResponse
    )
    // GET endpoint with segments
    addAkkaHttpEndpoint[List[String], List[String]]("getTest/$name",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo[List[String]],
      paramsResponse
    )

    addAkkaHttpEndpoint[List[String], List[String]]("account/$accountGuid/report/$reportId",
      HttpMethods.GET,
      false,
      Seq(),
      EndpointType.INTERNAL,
      reqToRouteParams,
      echo[List[String]],
      paramsResponse
    )
  }

  def reqToHello(r: AkkaHttpRequest): Future[Message] = Future.successful(Message("Hello World"))
  def reqToPayloadMessage(r: AkkaHttpRequest): Future[Message] =
    RouteGenerator.entityToString(r.requestBody.get).map(Message)
  def reqToRouteParams(r: AkkaHttpRequest): Future[List[String]] = {
    Future.successful(r.segments.toList)
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
}
