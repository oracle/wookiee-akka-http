package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives.{provide, path => p, _}
import akka.http.scaladsl.server.{Directive0, Directive1}
import com.webtrends.harness.command.{BaseCommand, CommandBean}

/**
  * Use this class to create a command that can handle any number of endpoints with any
  * number of HTTP methods in a single class
  */
trait AkkaHttpMulti extends AkkaHttpBase { this: BaseCommand =>
  // Map of endpoints as keys to http methods as values, e.g. Map("account/$acctId", get)
  def allPaths: List[(String, Directive1[AkkaHttpPathSegments], Directive0)]


  // Method that adds all routes from allPaths
  override def createRoutes() = {
    allPaths.foreach { pth =>
      currentPath = pth._2
      addRoute(commandInnerDirective(new CommandBean, pth._1, pth._3))
    }
  }

  // Is changed to get past having to pass arguments to httpPath
  var currentPath: Directive1[AkkaHttpPathSegments] = p("default") & provide(new AkkaHttpPathSegments {})
  override def httpPath = {
    currentPath
  }
}
