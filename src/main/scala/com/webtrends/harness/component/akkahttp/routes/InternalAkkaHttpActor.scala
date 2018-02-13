/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp.routes

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.settings.ServerSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.{HActor, Harness}
import com.webtrends.harness.component.akkahttp._
import com.webtrends.harness.component.akkahttp.client.SimpleHttpClient
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.component.{ComponentHelper, ComponentRequest, StopComponent}
import com.webtrends.harness.health._
import com.webtrends.harness.service.ServiceManager
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData
import com.webtrends.harness.service.meta.ServiceMetaData
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.ext.{EnumNameSerializer, JodaTimeSerializers}
import org.json4s.{DefaultFormats, JValue, jackson}
import com.webtrends.harness.utils.FutureExtensions._
import scala.concurrent.Future
import scala.util.{Failure, Success}

object InternalAkkaHttpActor {
  def props(settings: InternalAkkaHttpSettings) = {
    Props(classOf[InternalAkkaHttpActor], settings.port, settings.interface, settings.serverSettings)
  }
}

case class AkkaHttpUnbind()

class InternalAkkaHttpActor(port: Int, interface: String, settings: ServerSettings) extends HActor
  with ComponentHelper
  with SimpleHttpClient {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  override implicit val materializer = ActorMaterializer()
  implicit val serialization = jackson.Serialization
  implicit val formats       = (DefaultFormats ++ JodaTimeSerializers.all) + new EnumNameSerializer(ComponentState)

  val serverSource = Http().bind(interface, port, settings = settings)
  val pingUrl = s"http://$interface:$port/ping"

  val healthActor = system.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = system.actorSelection(HarnessConstants.ServicesFullName)

  val baseRoutes =
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
      path("ping") {
        complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
      } ~
      path("healthcheck") {
        complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
      } ~
      pathPrefix("healthcheck") {
        path("lb") {
          complete((healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String])
        } ~
          path("nagios") {
            completeOrRecoverWith((healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String]) {failWith}
          } ~
          path("full") {
            complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
          }
      } ~
      path("metrics") {
        onComplete(componentRequest[StatusRequest, JValue]("wookiee-metrics", ComponentRequest(StatusRequest()))) {
          case Success(s) => complete(s.resp)
          case Failure(f) => failWith(f)
        }
      } ~
      pathPrefix("services") {
        pathEnd {
          complete((serviceActor ? GetMetaData(None)).mapTo[Seq[ServiceMetaData]])
        } ~
          path(Segment) { service =>
            complete((serviceActor ? GetMetaDataByName(service)).mapTo[ServiceMetaData])
          }
      }
  } ~ post {
      pathPrefix("services") {
        path(Segment / "restart") { service =>
          serviceActor ! ServiceManager.RestartService(service)
          complete(s"The service $service has been asked to restart")
        }
      } ~
        path("shutdown") {
          Harness.shutdown
          complete(s"The system is being shutdown: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        } ~
        path("restart") {
          Harness.restartActorSystem
          complete(s"The system is being restarted: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
        }
    }

  def staticRoutes = {
    val rootPath = config.getString(AkkaHttpManager.KeyStaticRoot)
    config.getString(AkkaHttpManager.KeyStaticType) match {
      case "file" =>
        getFromBrowseableDirectory(rootPath)
      case "jar" =>
        getFromResourceDirectory(rootPath)
      case _ =>
        getFromResourceDirectory(rootPath)
    }
  }

  val bindingFuture = serverSource
    .to(Sink.foreach { conn => conn.handleWith(RouteResult.route2HandlerFlow(routes)) })
    .run()

  bindingFuture.onComplete {
    case Success(s) =>
      log.info(s"akka-http internal-server bound to port $port on interface $interface")
    case Failure(f) =>
      log.error(s"Failed to bind akka-http internal-server: $f")
  }

  def routes =
    InternalAkkaHttpRouteContainer
      .getRoutes
      .foldLeft(ExternalAkkaHttpRouteContainer
        .getRoutes
        .foldLeft(baseRoutes ~ staticRoutes)(_ ~ _)
      )(_ ~ _)

  def unbind = {
    bindingFuture.flatMap(_.unbind())
  }

  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }

  // This should probably be overriden to get some custom information about the health of this actor
  override protected def getHealth: Future[HealthComponent] = super.getHealth

  override def checkHealth : Future[HealthComponent] = {
    getPing(pingUrl).mapAll {
      case Success(_) =>
        HealthComponent(self.path.toString, ComponentState.NORMAL, s"Healthy: Ping to $pingUrl.")
      case Failure(_) =>
        HealthComponent(self.path.toString, ComponentState.CRITICAL, s"Failed to ping server at $pingUrl.")
    }
  }

}