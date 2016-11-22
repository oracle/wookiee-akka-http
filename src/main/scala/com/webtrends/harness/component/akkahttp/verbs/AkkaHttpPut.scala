package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import akka.http.scaladsl.server.Directives.put

trait AkkaHttpPut extends AkkaHttpBase {
  this: BaseCommand =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = {
    put {
      super.commandInnerDirective(bean)
    }
  }
}
