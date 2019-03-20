package com.webtrends.harness.component.akkahttp.client.oauth

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

object Error {
  sealed abstract class Code(val value: String)
  case object InvalidRequest       extends Code("invalid_request")
  case object InvalidClient        extends Code("invalid_client")
  case object InvalidToken         extends Code("invalid_token")
  case object InvalidGrant         extends Code("invalid_grant")
  case object InvalidScope         extends Code("invalid_scope")
  case object UnsupportedGrantType extends Code("unsupported_grant_type")
  case object Unknown              extends Code("unknown")

  object Code {
    def fromString(code: String): Code = code match {
      case "invalid_request"        => InvalidRequest
      case "invalid_client"         => InvalidClient
      case "invalid_token"          => InvalidToken
      case "invalid_grant"          => InvalidGrant
      case "invalid_scope"          => InvalidScope
      case "unsupported_grant_type" => UnsupportedGrantType
      case _                        => Unknown
    }
  }

  class UnauthorizedException(val code: Code, val description: String, val response: HttpResponse)
      extends RuntimeException(s"$code: $description")

  object UnauthorizedException {
    case class UnauthorizedResponse(error: String, errorDescription: String)

    def fromHttpResponse(response: HttpResponse)(implicit ec: ExecutionContext, mat: Materializer): Future[UnauthorizedException] = {
      Unmarshal(response).to[UnauthorizedResponse].map { r =>
        new UnauthorizedException(Code.fromString(r.error), r.errorDescription, response)
      }
    }
  }
}
