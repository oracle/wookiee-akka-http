package com.webtrends.harness.component.akkahttp.errors

object Errors {
  sealed trait AkkaHttpError {
    val msg: String
  }

  case class AuthError(msg: String) extends AkkaHttpError
}
