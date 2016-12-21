package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpGet extends AkkaHttpBase {
  this: BaseCommand =>
  override def httpMethod: Directive0 = get
}


