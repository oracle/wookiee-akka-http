package com.webtrends.harness.component.spray

import scala.concurrent.Future

package object authentication {
  type OAuthTokenAuthenticator[T] = Option[Token] => Future[Option[T]]
}

package authentication {
  /**
    * Simple case class model of a token combination.
    */
  case class Token(scope: String, token: Option[String])
}
