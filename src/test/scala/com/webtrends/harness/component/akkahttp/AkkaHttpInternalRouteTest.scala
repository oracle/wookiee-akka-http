package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.webtrends.harness.command.{BaseCommand, BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.methods.AkkaHttpGet
import com.webtrends.harness.component.akkahttp.verbs.AkkaHttpInternal
import com.webtrends.harness.logging.Logger
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future

class AkkaHttpInternalRouteTest extends FunSuite
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest {

  test("Internal routes should not appear in external http server") {

    new AkkaHttpGet with AkkaHttpInternal with BaseCommand {
      override def path: String = "test"
      override val log = Logger(classOf[AkkaHttpInternalRouteTest].getName)

      override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
        Future.successful(AkkaHttpCommandResponse(None))
      }
    }

    ExternalAkkaHttpRouteContainer.isEmpty mustEqual true
    InternalAkkaHttpRouteContainer.isEmpty mustEqual false
    InternalAkkaHttpRouteContainer.getRoutes.size mustEqual 1
  }
}
