package com.webtrends.harness.component.akkahttp.client.oauth

import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.stream.Materializer

import scala.concurrent.Future

case class AccessToken (
    accessToken: String,
    tokenType: String,
    scope: Option[String],
    expiresIn: Int,
    refreshToken: Option[String]
)

object AccessToken {
  implicit def um: FromEntityUnmarshaller[AccessToken] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypes.`application/json`).map { str =>
      val values = str.split("\n").last.split(",")
      TestEntity(values.head, values.last.toDouble)
    }

  def apply(response: HttpResponse)(implicit mat: Materializer): Future[AccessToken] = {
    Unmarshal(response).to[AccessToken]
  }
}
