package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.{Command, CommandBean}

trait AkkaHttpGet extends AkkaHttpBase {
  this: Command =>
  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = get {
    super.commandInnerDirective(bean)
  }
}


