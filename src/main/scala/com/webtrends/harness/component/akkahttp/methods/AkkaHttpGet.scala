package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directives.{path => p}
import com.webtrends.harness.command.BaseCommand

trait AkkaHttpGet extends AkkaHttpMethod {
  this: BaseCommand =>
  override def method: HttpMethod = HttpMethods.GET
}


