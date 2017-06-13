package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase


trait AkkaHttpCompression extends AkkaHttpBase {
  this: BaseCommand =>

  override def httpMethod(method: HttpMethod): Directive0 = decodeRequest & encodeResponse & AkkaHttpBase.httpMethod(method)
}
