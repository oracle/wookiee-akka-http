package com.webtrends.harness.component.akkahttp.methods

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives.provide
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.component.akkahttp.directives.AkkaHttpCORS
import com.webtrends.harness.logging.Logger

/**
  * Use this class to create a command that can handle any number of endpoints with any
  * number of HTTP methods in a single class
  */
trait AkkaHttpMulti extends AkkaHttpBase with AkkaHttpCORS { this: BaseCommand =>
  private val internalLogger = Logger(getClass)

  // Map of endpoint names as keys to endpoint info
  def allPaths: List[Endpoint]

  // Method that is called for each endpoint object on addition, can override to do special logic
  def endpointExtraProcessing(end: Endpoint): Unit = {}

  // Get the values present on the URI, input T type must be of type Holder# (e.g. Holder1)
  // where # is the number of variable segments on the URI
  def getURIParams[T <: AkkaHttpPathSegments](bean: CommandBean): T = {
    val segs = bean.get(AkkaHttpBase.Segments)
    segs match {
      case Some(s) =>
        s match {
          case t: T => t
          case _ =>
            throw new IllegalStateException(s"${s.getClass} is not a subclass of AkkaHttpPathSegments")
        }
      case None =>
        throw new IllegalStateException(s"No URI Segments found for this request")
    }
  }

  // Can grab the body with this
  def getPayload[T](bean: CommandBean): Option[T] =
    bean.getValue[T](CommandBean.KeyEntity)

  // Get params that were put on the end of the query (past the ?)
  def getQueryParams(bean: CommandBean): Map[String, String] =
    bean.getValue[Map[String, String]](AkkaHttpBase.QueryParams).getOrElse(Map.empty[String, String])

  // Method that adds all routes from allPaths
  override def createRoutes() = {
    allPaths.foreach { endpoint =>
      var segCount = 0
      // Split the path into segments and map those to their akka http objects
      val segs = endpoint.path.split("/").filter(_.nonEmpty).toSeq
      try {
        // Combine all segments into an akka path
        val dir = segs.tail.foldLeft(segs.head.asInstanceOf[Any]) { (x, y) =>
          y match {
            case s1: String if s1.startsWith("$") =>
              segCount += 1
              (x match {
                case p: String => if (p.startsWith("$")) {
                  segCount += 1
                  Segment / Segment
                } else p / Segment
                case p: PathMatcher[Unit] if segCount == 1 => p / Segment
                case p: PathMatcher[Tuple1[String]] if segCount == 2 => p / Segment
                case p: PathMatcher[(String, String)] if segCount == 3 => p / Segment
                case p: PathMatcher[(String, String, String)] if segCount == 4 => p / Segment
                case p: PathMatcher[(String, String, String, String)] if segCount == 5 => p / Segment
                case p: PathMatcher[(String, String, String, String, String)] if segCount == 6 => p / Segment
              }).asInstanceOf[PathMatcher[_]]
            case s1: String =>
              (x match {
                case p: String => if (p.startsWith("$")) {
                  segCount += 1
                  Segment / s1
                } else p / s1
                case p: PathMatcher[Unit] if segCount == 0 => p / s1
                case p: PathMatcher[Tuple1[String]] if segCount == 1 => p / s1
                case p: PathMatcher[(String, String)] if segCount == 2 => p / s1
                case p: PathMatcher[(String, String, String)] if segCount == 3 => p / s1
                case p: PathMatcher[(String, String, String, String)] if segCount == 4 => p / s1
                case p: PathMatcher[(String, String, String, String, String)] if segCount == 5 => p / s1
              }).asInstanceOf[PathMatcher[_]]
          }
        }
        // Add holders for query params if applicable
        currentPath = segCount match {
          case 0 if segs.size == 1 => p(endpoint.path) & provide(new AkkaHttpPathSegments {})
          case 0 => p(dir.asInstanceOf[PathMatcher[Unit]]) & provide(new AkkaHttpPathSegments {})
          case 1 => p(dir.asInstanceOf[PathMatcher[Tuple1[String]]]).as(Holder1)
          case 2 => p(dir.asInstanceOf[PathMatcher[(String, String)]]).as(Holder2)
          case 3 => p(dir.asInstanceOf[PathMatcher[(String, String, String)]]).as(Holder3)
          case 4 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String)]]).as(Holder4)
          case 5 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String)]]).as(Holder5)
          case 6 => p(dir.asInstanceOf[PathMatcher[(String, String, String, String, String, String)]]).as(Holder6)
        }
        // Can override this method to do something else with the endpoint
        endpointExtraProcessing(endpoint)
        addRoute(commandInnerDirective(endpoint.path, endpoint.method))
      } catch {
        case ex: Throwable =>
          internalLogger.error(s"Error adding path ${endpoint.path}", ex)
          throw ex
      }
    }
  }

  // Is changed to get past having to pass arguments to httpPath
  var currentPath: Directive1[AkkaHttpPathSegments] = p("default") & provide(new AkkaHttpPathSegments {})
  override def httpPath = {
    ignoreTrailingSlash & currentPath
  }

  // Overriding this so that child classes won't have to worry about it
  override def path = ""

  // Used to set entity, won't need to override
  def maxSizeBytes: Long = 1.024e6.toLong
  // TODO Consider attempting to cast values to ints before placing onto bean as other frameworks did
  override def beanDirective(bean: CommandBean, url: String = "", method: HttpMethod = HttpMethods.GET): Directive1[CommandBean] = {
    // Grab all segments from the URI and put them directly on the bean
    val segs = bean.getValue[String](AkkaHttpBase.Path).getOrElse("").split("/").filter(_.startsWith("$"))
    def addIfPresent(index: Int, value: String): Unit = {
      if (index < segs.length) bean.addValue(segs(index).drop(1), value)
    }
    bean.get(AkkaHttpBase.Segments).collect {
      case prod: Product =>
        prod.productIterator.zipWithIndex.foreach {
          case (a, i) => addIfPresent(i, a.toString)
        }
      case _ => // Do nothing
    }
    // Add query params onto the bean directly
    getQueryParams(bean).foreach(keyVal => bean.addValue(keyVal._1, keyVal._2))
    // Do the unmarshalling of the request
    val entityClass = allPaths.find(e => url == e.path && method == e.method).flatMap(_.unmarshaller)
    if (entityClass.isDefined) {
      val ev: Manifest[AnyRef] = Manifest.classType(entityClass.get)
      val unmarsh = AkkaHttpBase.unmarshaller[AnyRef](ev, fmt = formats)
      (withSizeLimit(maxSizeBytes) & entity(as[Option[AnyRef]](unmarsh))).flatMap { entity =>
        entity.foreach(ent => bean.addValue(CommandBean.KeyEntity, ent))
        super.beanDirective(bean, url, method)
      }
    } else super.beanDirective(bean, url, method)
  }
}

// Class that holds Endpoint info to go in allPaths, url is the endpoint's path and any query params
// can be input with $param, e.g. /endpoint/$param/accounts, method is an HTTP method, e.g. GET,
// unmarshaller is a case class that can hold the extract of the json body input
case class Endpoint(path: String, method: HttpMethod, unmarshaller: Option[Class[_]] = None)
