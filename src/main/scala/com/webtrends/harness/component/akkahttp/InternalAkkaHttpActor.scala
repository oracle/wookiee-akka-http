package com.webtrends.harness.component.akkahttp

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.settings.ServerSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.{HActor, Harness}
import com.webtrends.harness.app.HarnessActor.ShutdownSystem
import com.webtrends.harness.component.{ComponentHelper, ComponentRequest, StopComponent}
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.health.{ApplicationHealth, HealthRequest, HealthResponseType}
import com.webtrends.harness.service.ServiceManager
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData
import com.webtrends.harness.service.meta.ServiceMetaData
import org.joda.time.{DateTime, DateTimeZone}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, JValue, jackson}

import scala.util.{Failure, Success}

object InternalAkkaHttpActor {
  def props(settings: InternalAkkaHttpSettings) = {
    Props(classOf[InternalAkkaHttpActor], settings.port, settings.interface, settings.serverSettings)
  }
}

class InternalAkkaHttpActor(port: Int, interface: String, settings: ServerSettings) extends HActor with ComponentHelper {

  implicit val serialization = jackson.Serialization
  implicit val formats       = DefaultFormats ++ JodaTimeSerializers.all

  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  implicit val materializer = ActorMaterializer()

  val healthActor = system.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = system.actorSelection(HarnessConstants.ServicesFullName)

  val routes = get {
    path("favicon.ico") {
        complete(StatusCodes.NoContent)
    } ~
    path("ping") {
      complete(s"pong: ${new DateTime(System.currentTimeMillis(), DateTimeZone.UTC)}")
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
  } ~
  post {
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

  val bindingFuture = Http().bindAndHandle(routes, interface, port)

  bindingFuture.onComplete {
    case Success(s) =>
      log.info(s"akka-http internal-server bound to port $port on interface $interface")
    case Failure(f) =>
      log.error(s"Failed to bind akka-http internal-server: $f")
  }



  def unbind = bindingFuture.flatMap(_.unbind())


  override def receive = super.receive orElse {
    case AkkaHttpUnbind => unbind
    case StopComponent => unbind
  }
}
