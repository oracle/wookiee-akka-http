package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import com.webtrends.harness.command.{BaseCommand, Command, MapBean}
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}

trait AkkaHttpOptions extends AkkaHttpBase {
  this: Command[MapBean, AkkaHttpCommandResponse[_]] =>
  override def method: HttpMethod = HttpMethods.OPTIONS
}
