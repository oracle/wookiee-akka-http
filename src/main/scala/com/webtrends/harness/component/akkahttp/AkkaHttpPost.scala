package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.{Command, CommandBean}

trait AkkaHttpPost extends AkkaHttpBase {
  this: Command =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = post {
    super.commandInnerDirective(bean)
  }

}
