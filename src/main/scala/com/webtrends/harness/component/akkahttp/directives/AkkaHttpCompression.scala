package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpMethod


trait AkkaHttpCompression extends AkkaHttpMethod {
  this: BaseCommand =>

  override def httpMethod: Directive0 = decodeRequest & encodeResponse & super.httpMethod
}
