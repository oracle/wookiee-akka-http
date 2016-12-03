package com.webtrends.harness.component.akkahttp.util

import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean}
import com.webtrends.harness.logging.Logger

import scala.concurrent.Future

trait TestBaseCommand extends BaseCommand {
  override val log = Logger("test logger")
  def path: String
  def execute[T:Manifest](bean: Option[CommandBean]) : Future[BaseCommandResponse[T]]
}
