package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import com.webtrends.harness.command.BaseCommand

trait AkkaHttpOptions extends AkkaHttpMethod {
  this: BaseCommand =>
  override def method: HttpMethod = HttpMethods.OPTIONS
}
