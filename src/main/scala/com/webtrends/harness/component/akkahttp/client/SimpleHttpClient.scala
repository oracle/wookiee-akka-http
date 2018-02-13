package com.webtrends.harness.component.akkahttp.client

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

trait SimpleHttpClient { this: Actor =>

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
}