package com.webtrends.harness.component.akkahttp

import akka.actor.Props
import akka.http.scaladsl.model.StatusCodes
import com.webtrends.harness.command.Command
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpRequest, AkkaHttpResponse}

import scala.concurrent.Future

class SimpleCommandActor extends Command[AkkaHttpRequest, AkkaHttpResponse[AkkaHttpRequest]] {
  override def execute(bean: AkkaHttpRequest): Future[AkkaHttpResponse[AkkaHttpRequest]] = {
    Future.successful(AkkaHttpResponse(Some(bean), statusCode=Some(StatusCodes.OK)))
  }
}

object SimpleCommandActor {
  def apply(): Props = Props(new SimpleCommandActor())
}


