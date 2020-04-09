package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import com.webtrends.harness.command.{BaseCommand, Command, MapBean}
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}


trait AkkaHttpCompression extends AkkaHttpBase {
  this: Command[MapBean, AkkaHttpCommandResponse[_]] =>

  override def httpMethod(method: HttpMethod): Directive0 = decodeRequest & encodeResponse & super.httpMethod(method)
}
