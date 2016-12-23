package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpPost extends AkkaHttpBase {
  this: BaseCommand =>
  override def httpMethod: Directive0 = post
}
