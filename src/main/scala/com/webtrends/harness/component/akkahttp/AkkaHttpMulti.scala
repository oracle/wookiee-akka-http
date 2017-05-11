package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives.{entity, provide, path => p, _}
import akka.http.scaladsl.server.{Directive0, Directive1}
import com.webtrends.harness.command.{BaseCommand, CommandBean}

/**
  * Use this class to create a command that can handle any number of endpoints with any
  * number of HTTP methods in a single class
  */
trait AkkaHttpMulti extends AkkaHttpBase { this: BaseCommand =>
  // Map of endpoints as keys to http methods as values, e.g. Map("account/$acctId", get)
  def allPaths: Map[String, (Directive1[AkkaHttpPathSegments], Directive0, Option[Class[_]])]

  // Convenient method for when your path will not have any segments
  def emptyPath(pth: String) = p(pth) & provide(new AkkaHttpPathSegments {})

  // Method that adds all routes from allPaths
  override def createRoutes() = {
    allPaths.foreach { pth =>
      currentPath = pth._2._1
      addRoute(commandInnerDirective(new CommandBean, pth._1, pth._2._2))
    }
  }

  // Is changed to get past having to pass arguments to httpPath
  var currentPath: Directive1[AkkaHttpPathSegments] = p("default") & provide(new AkkaHttpPathSegments {})
  override def httpPath = {
    currentPath
  }

  // Used to set entity, won't need to override
  def maxSizeBytes: Long = 1.024e6.toLong
  override def beanDirective(bean: CommandBean, pathName: String = ""): Directive1[CommandBean] = {
    val entityClass = allPaths.get(pathName).flatMap(_._3)
    if (entityClass.isDefined) {
      val ev: Manifest[AnyRef] = Manifest.classType(entityClass.get)
      val unmarsh = AkkaHttpBase.unmarshaller[AnyRef](ev)
      (withSizeLimit(maxSizeBytes) & entity(as[AnyRef](unmarsh))).flatMap { entity =>
        bean.addValue(CommandBean.KeyEntity, entity)
        super.beanDirective(bean, pathName)
      }
    } else super.beanDirective(bean, pathName)
  }
}
