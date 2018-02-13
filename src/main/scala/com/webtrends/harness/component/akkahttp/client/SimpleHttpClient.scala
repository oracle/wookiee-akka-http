package com.webtrends.harness.component.akkahttp.client

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.webtrends.harness.logging.ActorLoggingAdapter

import scala.concurrent.{ExecutionContext, Future}

trait SimpleHttpClient extends ActorLoggingAdapter { this: Actor =>

  implicit val executionContext: ExecutionContext
  implicit val materializer = ActorMaterializer()

  private val http = Http(context.system)

  def request(req: HttpRequest): Future[(HttpResponse, Array[Byte])] = {
    http.singleRequest(req).flatMap { response =>
      response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(bs => (response, bs.toArray))
    }
  }

  def requestAs[T](req: HttpRequest, mapper: Array[Byte] => T): Future[(HttpResponse, T)] = {
    request(req).map { case (response, bytes) =>
      (response, mapper(bytes))
    }
  }

  def requestAsString(req: HttpRequest): Future[(HttpResponse, String)] = {
    requestAs[String](req, b => new String(b, "utf-8"))
  }

  def getPing(url: String) : Future[Boolean] = {
    val response = requestAsString(HttpRequest(HttpMethods.GET, url))

    response.map {
      case (response,body) if response.status == StatusCodes.Success =>
        body.startsWith("pong")
      case (response,body) =>
        log.error(s"Unexpected response from ping check with status ${response.status}: $body")
        false
    }
  }
}