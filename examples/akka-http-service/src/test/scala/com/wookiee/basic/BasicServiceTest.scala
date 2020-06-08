package com.wookiee.basic

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import com.webtrends.harness.service.test.{TestComponent, TestHarness, TestService}
import org.scalatest.WordSpecLike

class BasicServiceTest extends WordSpecLike {
  val sys: TestHarness = TestHarness(ConfigFactory.empty(), Some(Map("base" -> classOf[TestService])),
    Some(Map("testcomponent" -> classOf[TestComponent])))
  implicit val actorSystem: ActorSystem = sys.system

  "BasicService" should {
    "start itself up" in {
      val probe = TestProbe()
      val testService = sys.getService("base")
      assert(testService.isDefined, "Basic Service was not registered")

      probe.send(testService.get, GetMetaDetails)
      assert(ServiceMetaDetails(false) == probe.expectMsg(ServiceMetaDetails(false)))
    }
  }
}
