package com.webtrends.harness.component.akkahttp

import akka.actor.Props
import com.webtrends.harness.command.Command
import com.webtrends.harness.component.akkahttp.routes.AkkaHttpRequest

import scala.concurrent.Future

class SimpleCommandActor extends Command[AkkaHttpRequest, AkkaHttpRequest] {
  override def execute(input: AkkaHttpRequest): Future[AkkaHttpRequest] = {
    Future.successful(input)
  }
}

object SimpleCommandActor {
  def apply(): Props = Props(new SimpleCommandActor())
}


