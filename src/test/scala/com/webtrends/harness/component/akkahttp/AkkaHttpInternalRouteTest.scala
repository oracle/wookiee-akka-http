package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.util.{ErrorEntity, TestBaseCommand}
import com.webtrends.harness.component.akkahttp.verbs.{AkkaHttpGet, AkkaHttpInternal}
import org.scalatest.{FunSuite, MustMatchers}
import org.scalatest.prop.PropertyChecks

import scala.concurrent.Future

class AkkaHttpInternalRouteTest extends FunSuite
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest {

  test("Internal routes should not appear in external http server") {

    new AkkaHttpGet with AkkaHttpInternal with TestBaseCommand {
      override def path: String = "test"

      override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        Future.successful(AkkaHttpCommandResponse(None))
      }
    }

    ExternalAkkaHttpRouteContainer.isEmpty mustEqual true
    InternalAkkaHttpRouteContainer.isEmpty mustEqual false
    InternalAkkaHttpRouteContainer.getRoutes.size mustEqual 1
  }
}
