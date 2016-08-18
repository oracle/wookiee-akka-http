package com.webtrends.harness.component.spray.authentication

import spray.http.HttpHeaders.`WWW-Authenticate`
import spray.http._
import spray.routing.authentication.HttpAuthenticator
import spray.routing.{RequestContext, RoutingSettings}

import scala.concurrent.ExecutionContext

/**
 * The OAuthHttpAuthenticator implements HTTP Auth.
 */
class OAuthHttpAuthenticator[U](val scope: String, val oauthAuthenticator: OAuthTokenAuthenticator[U])
                               (implicit val executionContext: ExecutionContext)
  extends HttpAuthenticator[U] {

  def scheme = "OAuth"

  def params(ctx: RequestContext) = Map.empty

  def realm = scope

  def authenticate(credentials: Option[HttpCredentials], ctx: RequestContext) = {
    oauthAuthenticator(if (credentials.isEmpty) Option[Token](null) else Some(Token(scope, credentials.flatMap {
      case OAuth2BearerToken(token) => Some(token)
      case _ => Option[String](null)
    })))
  }

  def getChallengeHeaders(httpRequest: HttpRequest) =
    // Returning Basic scheme here to make sure that we return a basic header to get the username/password dialog
    `WWW-Authenticate`(HttpChallenge(scheme = "Basic", realm = scope, params = Map.empty)) :: Nil
}

object OAuth {

  def apply[T](scope: String)(implicit settings: RoutingSettings,
                              ec: ExecutionContext,
                              tokenAuthenticator: OAuthTokenAuthenticator[T]): OAuthHttpAuthenticator[T] =
    apply[T](tokenAuthenticator, scope)


  def apply[T](authenticator: OAuthTokenAuthenticator[T], scope: String)
              (implicit ec: ExecutionContext): OAuthHttpAuthenticator[T] =
    new OAuthHttpAuthenticator[T](scope, authenticator)
}