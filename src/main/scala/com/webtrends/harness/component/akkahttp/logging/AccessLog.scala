package com.webtrends.harness.component.akkahttp.logging

import akka.http.scaladsl.model.{DateTime, HttpRequest, StatusCode}
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.TimeOfRequest
import com.webtrends.harness.component.akkahttp.logging.AccessLog._
import com.webtrends.harness.logging.Logger

trait AccessLog  {
  this: BaseCommand =>

  val accessLog = Logger("AccessLog")

  // Override to obtain the id
  // id is no longer limited to user, with oauth or other authentication/authorization there are other ids to consider.
  // Like client id.
  def getAccessLogId(bean: CommandBean): Option[String] = {
    None
  }

  def logAccess(request: HttpRequest, bean: CommandBean, statusCode: Option[StatusCode]) = if (accessLoggingEnabled) {

    // modify the logback.xml file to write the "AccessLog" entries to a file without all of the prefix information
    try {
      val id: String = getAccessLogId(bean).getOrElse("-")
      val status: String = statusCode.map(sc => sc.intValue.toString).getOrElse("-")
      val responseTimestamp: Long = System.currentTimeMillis()
      val requestTimestamp: Long = bean.getValue[Long](TimeOfRequest).getOrElse(responseTimestamp)
      val elapsedTime: Long = responseTimestamp - requestTimestamp
      val requestTime: String = DateTime(requestTimestamp).toIsoDateTimeString()
      /*
          LogFormat "%h %l %u %t \"%r\" %>s %b %{ms}T"

          %h – The IP address of the server.
          %l – The identity of the client determined by identd on the client’s machine. Will return a hyphen (-) if this information is not available.
          %u – The id of the client if the request was authenticated.
          %t – The time that the request was received, in UTC
          \"%r\" – The request line that includes the HTTP method used, the requested resource path, and the HTTP protocol that the client used.
          %>s – The status code that the server sends back to the client.
          %b – The size of the object requested. Will return a hyphen (-) if this information is not available.
          %{ms}T - The time taken to serve the request, in milliseconds

          see https://httpd.apache.org/docs/2.4/logs.html
      */

      val headers = request.headers
      val origin = headers.find(header => header.name() == "Origin").map(_.value()).getOrElse("-")
      val user_agent = headers.find(header => header.name() == "User-Agent").map(_.value()).getOrElse("-")

      accessLog.info( s"""${AccessLog.host} - $id [$requestTime] "${request.method.value} ${request.uri} ${request.protocol.value}" $status - $elapsedTime - $origin - $user_agent""")

    } catch {
      case e: Exception =>
        accessLog.error("Could not construct access log", e)
    }
  }
}

object AccessLog {
  var accessLoggingEnabled = true
  val host: String = java.net.InetAddress.getLocalHost.getHostName
}