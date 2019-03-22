package com.webtrends.harness.component.akkahttp.client.oauth.token

import akka.http.scaladsl.model.{ContentTypes, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import akka.stream.Materializer
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.Future

case class AccessToken (
                         access_token: String,
                         token_type: String,
                         scope: Option[String],
                         expires_in: Int,
                         refresh_token: Option[String]
)

object AccessToken {
  implicit val formats: Formats = DefaultFormats
  implicit def um: FromEntityUnmarshaller[AccessToken] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypes.`application/json`).map { str =>
      parse(str).extract[AccessToken]
    }

  def apply(response: HttpResponse)(implicit mat: Materializer): Future[AccessToken] = {
    Unmarshal(response).to[AccessToken]
  }
}
