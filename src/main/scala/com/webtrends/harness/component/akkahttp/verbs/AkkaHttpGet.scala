package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.CommandLike

trait AkkaHttpGet extends AkkaHttpBase {
  this: CommandLike =>
  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = get {
    super.commandInnerDirective(bean)
  }
}


