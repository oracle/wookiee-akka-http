package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpPost extends AkkaHttpBase {
  this: BaseCommand =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = post {
    super.commandInnerDirective(bean)
  }

}
