package com.wookiee.basic.command

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpMulti, Endpoint}

import scala.concurrent.Future

/**
  * Registers itself on both the external and internal http servers by default
  * and is accessible through any of the routes/methods in allPaths.
  */
class AkkaHttpReadCommand extends Command with AkkaHttpMulti {
  override def allPaths = List(Endpoint("get/$someUrlKey", HttpMethods.GET))

  /**
    * Name of the command that will be used for the actor name
    */
  override def commandName = AkkaHttpReadCommand.CommandName

  override def process(bean: CommandBean): PartialFunction[(String, HttpMethod), Future[BaseCommandResponse[_]]] = {
    case ("get/$someUrlKey", HttpMethods.GET) =>
      Future.successful(CommandResponse(Some(bean("someUrlKey"))))
  }
}

object AkkaHttpReadCommand {
  val CommandName = "AkkaHttpReadCommand"
}