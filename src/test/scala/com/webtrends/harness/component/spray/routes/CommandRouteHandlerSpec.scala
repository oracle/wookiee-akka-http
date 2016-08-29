package com.webtrends.harness.component.spray.routes

import akka.testkit.TestActorRef
import com.webtrends.harness.command.{CommandException, CommandBean, Command}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{CommandRouteHandler, SprayGet, RouteManager}
import org.specs2.mutable.SpecificationWithJUnit
import spray.routing.{Directives, HttpService}
import spray.testkit.Specs2RouteTest

import scala.concurrent.Future

class ExceptionCommand extends Command with SprayGet{
  override def commandName: String = "ExceptionCommand"
  override def path: String = "/test/ExceptionCommand"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    throw CommandException("ExceptionCommand", new Exception("Do not leak this"))
  }
}

class ExceptionDebugCommand extends Command with SprayGet with CommandRouteHandler {
  override def commandName: String = "ExceptionDebugCommand"
  override def path: String = "/test/ExceptionDebugCommand"

  override def exceptionHandler = debugExceptionHandler

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    throw new Exception("Leak this")
  }
}

class CommandRouteHandlerSpec extends SpecificationWithJUnit with Directives with Specs2RouteTest with HttpService {
  def actorRefFactory = system

  TestActorRef[ExceptionCommand]
  TestActorRef[ExceptionDebugCommand]

  "Default exception handler" should {
    "not include exception details in response" in {
      Get("/test/ExceptionCommand") ~> RouteManager.getRoute("ExceptionCommand_get").get ~> check {
        status.intValue mustEqual 500
        responseAs[String] must not contain("Do not leak this")
      }
    }
  }

  "debug exception handler" should {
    "include exception details in response" in {
      Get("/test/ExceptionDebugCommand") ~> RouteManager.getRoute("ExceptionDebugCommand_get").get ~> check {
        status.intValue mustEqual 500
        responseAs[String] must contain("Leak this")
      }
    }
  }

}
