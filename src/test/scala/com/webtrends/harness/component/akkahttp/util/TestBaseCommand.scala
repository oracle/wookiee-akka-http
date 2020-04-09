package com.webtrends.harness.component.akkahttp.util

import com.webtrends.harness.command.{BaseCommand, MapBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpCommandResponse
import com.webtrends.harness.logging.Logger

import scala.concurrent.Future

trait TestBaseCommand extends BaseCommand {
  def path: String
  def execute[T:Manifest](bean: Option[MapBean]) : Future[AkkaHttpCommandResponse[_]]
}
