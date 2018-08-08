package com.webtrends.harness.component.akkahttp.logging

import akka.http.scaladsl.model.{DateTime, HttpMethod, StatusCode}
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.TimeOfRequest
import com.webtrends.harness.logging.Logger

trait AccessLog  {
  this: BaseCommand =>

  val accessLog = Logger("AccessLog")
  def logAccess(bean: CommandBean, statusCode: Option[StatusCode] = None) {

    def reconstructPath(bean: CommandBean): String = {
      val path: String = bean.getValue[String](AkkaHttpBase.Path).getOrElse("-")
      val segments = path.split("/").filter(_.startsWith("$"))
      segments.foldLeft(path){(p,s) => bean.getValue[String](s.drop(1)).map(value => p.replace(s, value)).getOrElse(p)}
    }

    try {
      val host: String = bean.getValue[Map[String,String]](AkkaHttpBase.RequestHeaders).flatMap(rh => rh.get("host")).getOrElse("-")
      // the userId may be either a String or an Int
      val user: String = bean.get("userId").map(id => id.toString).getOrElse("-")
      val status:String = statusCode.map(sc => sc.intValue.toString).getOrElse("-")
      val responseTimestamp: Long = System.currentTimeMillis()
      val requestTimestamp: Long = bean.getValue[Long](TimeOfRequest).getOrElse(responseTimestamp)
      val elapsedTime: Long = responseTimestamp - requestTimestamp
      val now: String = DateTime(requestTimestamp).toIsoDateTimeString()
      val method: String = bean.get(AkkaHttpBase.Method).map {
        case m: HttpMethod => m.value
        case m => m.toString
      }.getOrElse("-")
      val path: String = reconstructPath(bean)
      /*
          LogFormat "%h %l %u %t \"%r\" %>s %b %{ms}T"

          %h – The IP address of the client.
          %l – The identity of the client determined by identd on the client’s machine. Will return a hyphen (-) if this information is not available.
          %u – The userid of the client if the request was authenticated.
          %t – The time that the request was received.
          \"%r\" – The request line that includes the HTTP method used, the requested resource path, and the HTTP protocol that the client used.
          %>s – The status code that the server sends back to the client.
          %b – The size of the object requested. Will return a hyphen (-) if this information is not available.
          %{ms}T - The time taken to serve the request, in milliseconds

          see https://httpd.apache.org/docs/2.4/logs.html
      */
      accessLog.info( s"""$host - $user [$now] "$method $path -" $status - $elapsedTime""")
    } catch {
      case e: Exception =>
        log.error("Could not construct access log", e)
    }
  }

}
