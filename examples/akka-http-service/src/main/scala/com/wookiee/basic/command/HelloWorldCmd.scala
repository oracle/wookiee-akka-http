/*
package com.wookiee.basic.command

import akka.http.javadsl.server.Route
import akka.http.scaladsl.model.HttpMethods
import akka.util.Timeout
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, EndpointType}
import com.webtrends.harness.logging.Logger

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.Directives.complete

trait HelloWorldCmd  extends AkkaHttpEndpointRegistration {
  implicit val timeout = Timeout(2 seconds)
  implicit val logger: Logger = Logger.getLogger(getClass.getName)
  def responseHandler: PartialFunction[Any, Route] =  {
    case(resp:String) => complete(resp)
  }

  addAkkaHttpEndpoint("getTest",
    HttpMethods.GET,
    false,
    Seq(),
    EndpointType.INTERNAL,
    (req:AkkaHttpRequest)=> Right(req),
    (req:AkkaHttpRequest) => Future.successful("Hello World"),
    responseHandler
  )

}
*/
