package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.{CommandBean, BaseCommand}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.JObject

trait AkkaHttpPost extends AkkaHttpBase {
  this: BaseCommand =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = post {
    super.commandInnerDirective(bean)
  }

}
