package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Directives.{entity, provide, path => p, _}
import akka.http.scaladsl.server.{Directive1, PathMatcher}
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, FieldSerializer, Formats}

/**
  * Use this class to create a command that can handle any number of endpoints with any
  * number of HTTP methods in a single class
  */
trait AkkaHttpMulti extends AkkaHttpBase { this: BaseCommand =>
  // Map of endpoint names as keys to endpoint info
  def allPaths: Map[String, Endpoint]

  // Convenient method for when your path will not have any segments
  def emptyPath(pth: String) = p(pth) & provide(new AkkaHttpPathSegments {})

  // Method that is called for each endpoint object on addition, can override to do special logic
  def endpointExtraProcessing(end: Endpoint): Unit = {}

  // Method that adds all routes from allPaths
  override def createRoutes() = {
    allPaths.foreach { case (pathName, endpoint) =>
      var segCount = 0
      // Map whatever method this is to its akka http object
      val methUp = endpoint.method.toUpperCase()
      val meth = Map("GET" -> get, "PUT" -> put, "DELETE" -> delete, "POST" -> post,
        "OPTIONS" -> options)(methUp)
      // Split the path into segments and map those to their akka http objects
      val segs = endpoint.url.split("/").filter(_.nonEmpty).map(_.asInstanceOf[Any]).toSeq
      // Combine all segments into an akka path
      val headX = segs.head match {
        case s1: String if s1.startsWith("$") => Segment
        case s1: String => s1 unary_!()
        case s: PathMatcher[_] => s
      }
      val dir = segs.tail.foldLeft(headX) { (x, y) => y match {
          case s1: String if s1.startsWith("$") => x / s1
          case s1: String => x / s1
        }
      }
      // Add holders for query params if applicable
      currentPath = if (segCount > 0) {
        segCount match {
          case 1 => p(dir.asInstanceOf[PathMatcher[Tuple1[String]]]).as(Holder1)
          case 2 => p(dir.asInstanceOf[PathMatcher[(String, String)]]).as(Holder2)
          case 3 => p(dir.asInstanceOf[PathMatcher[(String, String, String)]]).as(Holder3)
          case 4 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String)]]).as(Holder4)
          case 5 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String)]]).as(Holder5)
        }
      } else {
        emptyPath(endpoint.url)
      }
      // Can override this method to do something else with the endpoint
      endpointExtraProcessing(endpoint)
      addRoute(commandInnerDirective(new CommandBean, pathName, meth))
    }
  }

  // Is changed to get past having to pass arguments to httpPath
  var currentPath: Directive1[AkkaHttpPathSegments] = p("default") & provide(new AkkaHttpPathSegments {})
  override def httpPath = {
    currentPath
  }

  // Overriding this so that child classes won't have to worry about it
  override def path = ""

  // Used to set entity, won't need to override
  def maxSizeBytes: Long = 1.024e6.toLong
  override def beanDirective(bean: CommandBean, pathName: String = ""): Directive1[CommandBean] = {
    val entityClass = allPaths.get(pathName).flatMap(_.unmarshaller)
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

// Class that holds Endpoint info to go in allPaths, url is the endpoint's path and any query params
// can be input with $param, e.g. /endpoint/$param/accounts, method is an HTTP method, e.g. GET,
// unmarshaller is a case class that can hold the extract of the json body input
case class Endpoint(url: String, method: String, unmarshaller: Option[Class[_]] = None)
