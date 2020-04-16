package com.wookiee.basic.command

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import com.webtrends.harness.command.{Command, MapBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpCommandResponse
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpMulti, Endpoint}

import scala.concurrent.Future

/**
  * Registers itself on both the external and internal http servers by default
  * and is accessible through any of the routes/methods in allPaths.
  */
class AkkaHttpReadCommand extends Command[MapBean, AkkaHttpCommandResponse[_]] with AkkaHttpMulti {
  override def allPaths = List(Endpoint("get/$someUrlKey", HttpMethods.GET))

  /**
    * Name of the command that will be used for the actor name
    */
  def commandName = AkkaHttpReadCommand.CommandName

  override def process(bean: MapBean): PartialFunction[(String, HttpMethod), Future[AkkaHttpCommandResponse[_]]] = {
    case ("get/$someUrlKey", HttpMethods.GET) =>
      Future.successful(AkkaHttpCommandResponse(Some(bean)))
  }
}

object AkkaHttpReadCommand {
  val CommandName = "AkkaHttpReadCommand"
}