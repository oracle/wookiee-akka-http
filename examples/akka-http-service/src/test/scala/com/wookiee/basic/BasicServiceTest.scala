package com.wookiee.basic

import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.service.messages.GetMetaDetails
import com.webtrends.harness.service.meta.ServiceMetaDetails
import com.webtrends.harness.service.test.{BaseWookieeTest, TestHarness}
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class BasicServiceTest extends BaseWookieeTest with WordLikeSpec {
  override def config = ConfigFactory.empty()
  override def servicesMap = Some(Map("base" -> classOf[AkkaHttpService]))

  "BasicService" should {
    "start itself up" in {
      val probe = TestProbe()
      val testService = TestHarness.harness.get.getService("base")
      assert(testService.isDefined, "Basic Service was not registered")

      probe.send(testService.get, GetMetaDetails)
      ServiceMetaDetails(false) mustEqual probe.expectMsg(ServiceMetaDetails(false))
    }
  }
}
