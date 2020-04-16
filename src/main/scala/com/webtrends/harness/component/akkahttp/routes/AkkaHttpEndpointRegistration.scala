package com.webtrends.harness.component.akkahttp.routes

import akka.http.scaladsl.model.{HttpHeader, HttpMethod}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.webtrends.harness.command.CommandHelper
import com.webtrends.harness.logging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object EndpointType extends Enumeration {
  type EndpointType = Value
  val INTERNAL, EXTERNAL, WEBSOCKET = Value
}

trait AkkaHttpEndpointRegistration {
  this: CommandHelper =>

  // TODO: new prop for enableHealthcheck?
  def addAkkaHttpEndpoint[T <: Product: ClassTag, U: ClassTag](path: String,
                                                               method: HttpMethod,
                                                               enableCors: Boolean,
                                                               defaultHeaders: Seq[HttpHeader],
                                                               endpointType: EndpointType.EndpointType,
                                                               requestHandler: AkkaHttpRequest => Either[Throwable, T],
                                                               businessLogic: T => Future[U],
                                                               responseHandler: PartialFunction[Any, Route],
                                                              )(implicit ec: ExecutionContext, log: Logger, to: Timeout): Unit = {

      addCommand(path, businessLogic).map { ref =>
        val route = RouteGenerator.makeRoute(path, method, defaultHeaders, enableCors, ref, requestHandler, responseHandler)

        endpointType match {
          case EndpointType.INTERNAL =>
            InternalAkkaHttpRouteContainer.addRoute(route)
          case EndpointType.EXTERNAL =>
            ExternalAkkaHttpRouteContainer.addRoute(route)
          case EndpointType.WEBSOCKET =>
            // TODO: I can't imaging this uses the same directive as internal/external
            ???
        }
      }
    }
}
