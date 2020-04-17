package com.webtrends.harness.component.akkahttp.client.oauth.token

sealed abstract class GrantType(val value: String)

object GrantType {
  case object AuthorizationCode   extends GrantType("authorization_code")
  case object ClientCredentials   extends GrantType("client_credentials")
  case object PasswordCredentials extends GrantType("password")
  case object Implicit            extends GrantType("implicit")
  case object RefreshToken        extends GrantType("refresh_token")
}
