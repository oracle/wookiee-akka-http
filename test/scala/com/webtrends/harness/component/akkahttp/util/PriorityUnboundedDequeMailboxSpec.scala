package com.webtrends.harness.component.akkahttp.util

import akka.actor.{Actor, ActorSystem}
import akka.dispatch.Envelope
import akka.testkit.TestKit
import com.webtrends.harness.component.akkahttp.websocket.PriorityUnboundedDequeMailbox
import org.scalatest.{MustMatchers, WordSpecLike}

class PriorityUnboundedDequeMailboxSpec extends TestKit(ActorSystem("PriorityUnboundedDequeMailbox")) with WordSpecLike with MustMatchers {
  "PriorityUnboundedDequeMailbox " should {
    "prevent consecutive items" in {

      val queue = new PriorityUnboundedDequeMailbox(system.settings, null) {
        override def priority(e: Envelope) = false
        override def isDupe(lastEnv: Envelope, newEnv: Envelope) = {
          val lEnv = lastEnv.message.asInstanceOf[(String, Dupe)]
          val nEnv = newEnv.message.asInstanceOf[(String, Dupe)]
          lEnv._2 == nEnv._2
        }
      } create(None, None)

      trait Dupe
      case object DupeId1 extends Dupe
      case object DupeId2 extends Dupe
      case object DupeId3 extends Dupe

      queue.enqueue(null, Envelope(("1", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("2", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("3", DupeId3), Actor.noSender, system))
      queue.enqueue(null, Envelope(("4", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("5", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("6", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("7", DupeId1), Actor.noSender, system))
      queue.enqueue(null, Envelope(("8", DupeId2), Actor.noSender, system))
      queue.enqueue(null, Envelope(("9", DupeId2), Actor.noSender, system))

      queue.numberOfMessages mustEqual 6
      var values = for (n <- 0 until queue.numberOfMessages) yield queue.dequeue().message.asInstanceOf[(String, Dupe)]._1
      values mustEqual Seq("1", "2", "3", "4", "7", "9")
      queue.numberOfMessages mustEqual 0
    }
  }
}
