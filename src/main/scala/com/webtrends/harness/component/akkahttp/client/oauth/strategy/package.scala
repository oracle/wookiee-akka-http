package com.webtrends.harness.component.akkahttp.client.oauth

package object strategy {
  implicit val authorizationCodeStrategy: AuthorizationCodeStrategy     = new AuthorizationCodeStrategy
  implicit val clientCredentialsStrategy: ClientCredentialsStrategy     = new ClientCredentialsStrategy
  implicit val implicitStrategy: ImplicitStrategy                       = new ImplicitStrategy
  implicit val passwordCredentialsStrategy: PasswordCredentialsStrategy = new PasswordCredentialsStrategy
  implicit val refreshTokenStrategy: RefreshTokenStrategy               = new RefreshTokenStrategy
}
