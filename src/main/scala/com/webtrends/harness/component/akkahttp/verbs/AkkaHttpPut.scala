package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.put
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpPut extends AkkaHttpBase {
  this: BaseCommand =>
  override def httpMethod: Directive0 = put
}
