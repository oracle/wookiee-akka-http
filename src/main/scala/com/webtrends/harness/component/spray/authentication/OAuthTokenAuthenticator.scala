package com.webtrends.harness.component.spray.authentication

import spray.util.LoggingContext

import scala.concurrent.ExecutionContext

object OAuthTokenAuthenticator {
  def apply[T](f: OAuthTokenAuthenticator[T])(implicit ec: ExecutionContext, log: LoggingContext) = f
}
