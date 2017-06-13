package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpDelete extends AkkaHttpBase {
  this: BaseCommand =>
  override def method: HttpMethod = HttpMethods.DELETE
}


