package com.webtrends.harness.component.akkahttp.routes

import akka.actor.{Actor, ActorRef, Terminated}
import akka.http.scaladsl.model.ws.TextMessage
import com.webtrends.harness.app.Harness.log
import com.webtrends.harness.component.akkahttp.routes.RouteGenerator.{CloseSocket, Connect}

class SocketActor(requestHandler: SocketRequest => TextMessage) extends Actor {
  private[websocket] var callbactor: Option[ActorRef] = None

  override def postStop() = {
    //      openSocketGauge.incr(-1)
    //      onWebsocketClose(bean, callbactor)
    super.postStop()
  }

  override def preStart() = {
    //      openSocketGauge.incr(1)
    super.preStart()
  }

  def receive: Receive = starting

  def starting: Receive = {
    case Connect(actor) =>
      callbactor = Some(actor) // Set callback actor
      context become open()
      context.watch(actor)
    case _: CloseSocket =>
      context.stop(self)
  }

  // When becoming this, callbactor should already be set
  def open(): Receive = {
    case tmb: (TextMessage, WebsocketRequest) =>
      val returnText = requestHandler(SocketRequest(tmb._1, tmb._2, callbactor.get))
      callbactor.get ! returnText
    case Terminated(actor) =>
      if (callbactor.exists(_.path.equals(actor.path))) {
        log.debug(s"Linked callback actor terminated ${actor.path.name}, closing down websocket")
        context.stop(self)
      }
    case _: CloseSocket =>
      context.stop(self)
    case _ => // Mainly for eating the keep alive
  }
}
