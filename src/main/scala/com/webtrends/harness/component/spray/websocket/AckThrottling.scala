package com.webtrends.harness.component.spray.websocket

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Stash}
import akka.dispatch._
import akka.io.Tcp
import com.typesafe.config.Config
import spray.can.websocket.FrameCommandFailed
import spray.can.websocket.frame.{Frame, FrameRender, TextFrame}

/**
 * Credit to https://github.com/smootoo/simple-spray-websockets/blob/master/src/main/scala/org/suecarter/websocket/WebSocket.scala
 */
trait AckThrottling extends ActorLogging with Stash {

  def serverConnection: ActorRef
  def closeLogic: Receive

  def waitingForAck(sending: Frame): Receive = {
    case Ack =>
      context.unbecome()
      unstashAll()

    case FrameCommandFailed(frame: Frame, _) if frame == sending =>
      log.warning(s"Failed to send frame, retrying: $frame")

      serverConnection ! Tcp.ResumeWriting

      context.become(
        waitingForRecovery(frame) orElse closeLogic orElse stashing,
        discardOld = false
      )
  }

  def waitingForRecovery(frame: Frame): Receive = {
    case Tcp.WritingResumed =>
      serverConnection ! Tcp.Write(FrameRender(frame), Ack)
      context.unbecome()
  }

  def stashing: Receive = {
    case msg => stash()
  }

  def sendWithAck(frame: TextFrame): Unit = {
    serverConnection ! Tcp.Write(FrameRender(frame), Ack)
    context.become(closeLogic orElse waitingForAck(frame) orElse stashing, discardOld = false)
  }
}

private object Ack extends Tcp.Event with spray.io.Droppable {
  override def toString = "Ack"
}

/**
 * Copied from https://github.com/smootoo/simple-spray-websockets/blob/master/src/main/scala/org/suecarter/websocket/PriorityUnboundedDequeMailbox.scala
 *
 * When using `context.become` to wait for an `Ack`, then `Ack` will
 * normally be placed at the end of the queue. This custom mailbox
 * will prioritise `Ack` messages so that they are always placed at
 * the front of the queue.
 *
 * This showed a performance improvement of 1 hour to 2 minutes when
 * sending about 100,000 messages, as the client actor was spending
 * the vast majority of its time traversing the work queue and
 * re-stashing messages.
 */
case class HighPriorityAckMailbox(settings: ActorSystem.Settings, config: Config)
  extends PriorityUnboundedDequeMailbox(settings, config) {
  override def priority(e: Envelope): Boolean = e.message match {
    case Ack => true
    case fail: FrameCommandFailed => true
    case Tcp.WritingResumed => true
    case _ => false
  }
}
