package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives.{path => p, _}
import com.webtrends.harness.command.BaseCommand
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpMethod extends AkkaHttpBase {
  this: BaseCommand =>

  def method: HttpMethod

  override def httpMethod: Directive0 = method match {
    case HttpMethods.GET => get
    case HttpMethods.PUT => put
    case HttpMethods.POST => post
    case HttpMethods.DELETE => delete
    case HttpMethods.OPTIONS => options
  }
}
