package com.webtrends.harness.component.spray.directive

import com.webtrends.harness.command.Command
import com.webtrends.harness.component.spray.route.SprayOptions
import spray.http.HttpHeaders._
import spray.http._
import spray.routing.{Directive0, Directives}

sealed case class corsRequestContext(origin: Option[HttpOrigin], allowHeaders: Option[Seq[String]])

trait CORS extends SprayOptions with Directives { this: Command =>

  private val exposeResponseHeaderBlackList = Set(
    "cache-control", "content-language", "content-type", "expires", "last-modified", "pragma",
    "access-control-allow-origin", "access-control-allow-credentials")

  private def extractOrigin: PartialFunction[HttpHeader, HttpOrigin] = {
    case `Origin`(origins) => origins(0)
  }

  private def extractRequestAllowHeaders: PartialFunction[HttpHeader, Seq[String]] = {
    case `Access-Control-Request-Headers`(headers) => headers
  }

  /**
    * Added to the route for OPTIONS request.
    *
    * Default behavior:
    *   - Ignore requests without an Origin and let them pass through without applying any CORS logic
    *   - Echo the request's Origin back in the Access-Control-Allow-Origin header
    *   - Send Access-Control-Allow-Credentials: true
    *   - Send Access-Control-Max-Age as Long.MaxValue
    *   - Echo the value of Access-Control-Request-Headers back in the Access-Control-Allow-Headers header
    *
    * The default behavior can be customized for any Command by overriding the corresponding method(s) below
    */
  override def corsPreflight: Directive0 = {

    (optionalHeaderValuePF(extractOrigin) & optionalHeaderValuePF(extractRequestAllowHeaders)).as(corsRequestContext) flatMap {
      corsRequestContext =>
        corsRequestContext.origin match {
          case Some(origin) =>
            respondWithHeaders(List(
              Some(`Access-Control-Allow-Origin`(corsAllowedOrigins(origin))),
              Some(`Access-Control-Max-Age`(corsMaxAge)),
              corsAllowCredentials match {
                case true => Some(`Access-Control-Allow-Credentials`(true))
                case false => None
              },
              corsAllowedHeaders(corsRequestContext.allowHeaders) match {
                case Some(h) => Some(`Access-Control-Allow-Headers`(h))
                case None => None
              }
            ).flatten)
          case None =>
            if (corsRequired) {
              complete(StatusCodes.Unauthorized)
            }
            else {
              pass
            }
        }
    }
  }

  /**
    * Added to the route for all requests other than OPTIONS
    *
    * Default behavior:
    *   - Ignore requests without an Origin and let them pass through without applying any CORS logic
    *   - Echo the request's Origin back in the Access-Control-Allow-Origin header
    *   - Send Access-Control-Allow-Credentials: true
    *
    * The default behavior can be customized for any Command by overriding the corresponding method(s) below
    */
  override def corsRequest: Directive0 = {

    (optionalHeaderValuePF(extractOrigin) & optionalHeaderValuePF(extractRequestAllowHeaders)).as(corsRequestContext) flatMap {
      corsRequestContext =>
        corsRequestContext.origin match {
          case Some(origin) =>
            corsAllowedOrigins(origin) match {
              case SomeOrigins(origins) if origins.contains(origin) =>
                  respondWithHeaders(List(
                    `Access-Control-Allow-Origin`(corsAllowedOrigins(origin)),
                    `Access-Control-Allow-Credentials`(corsAllowCredentials)
                  ))
              case _ =>
                complete(StatusCodes.Unauthorized)
            }
          case None =>
            if (corsRequired) {
              complete(StatusCodes.Unauthorized)
            }
            else {
              pass
            }
        }
    }
  }


  /**
    * Added to the route for all requests other than OPTIONS
    *
    * Default behavior:
    *   - If the request contains an Origin header and the response includes any headers that are not considered
    *   simple response headers or CORS headers, those headers will be listed in the Access-Control-Expose-Headers
    *   response header
    */
  override def corsResponse: Directive0 = {

    (optionalHeaderValuePF(extractOrigin) & optionalHeaderValuePF(extractRequestAllowHeaders)).as(corsRequestContext) flatMap {
      corsRequestContext =>
        corsRequestContext.origin match {
          case Some(origin) =>
            mapHttpResponseHeaders { headers =>
              val exposeHeaders = headers.filterNot( h => exposeResponseHeaderBlackList.contains(h.lowercaseName))
                .map(_.name)
                .sortWith((a, b) => a < b)

              if (exposeHeaders.nonEmpty) {
                headers :+ `Access-Control-Expose-Headers`(exposeHeaders)
              }
              else {
                headers
              }
            }
          case None =>
            pass
        }
    }
  }

  /**
    * Override within a command to require CORS
    *
    * If required, and a request does not include the Origin header, it will be rejected with a 401
    */
  def corsRequired = false

  /**
    * Override within a command to limit the origins which can access the resource
    *
    * Note that defining what origins are allowed doesn't automatically enforce use of CORS.
    */
  def corsAllowedOrigins(requestOrigin: HttpOrigin): AllowedOrigins = SomeOrigins(Seq(requestOrigin))


  /**
    * Override within a command to disable the Access-Control-Allow-Credentials response header
    */
  def corsAllowCredentials = true

  /**
    * Override within a command to set a new value for the Access-Control-Max-Age preflight response header
    */
  def corsMaxAge = Long.MaxValue

  /**
    * Override within a command to set a new value for the Access-Control-Allow-Headers preflight response header
    */
  def corsAllowedHeaders(requestHeaders: Option[Seq[String]]) = requestHeaders
}
