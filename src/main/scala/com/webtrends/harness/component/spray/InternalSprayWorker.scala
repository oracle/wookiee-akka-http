package com.webtrends.harness.component.spray

import _root_.spray.http.StatusCodes
import _root_.spray.routing.Route
import akka.pattern.ask
import akka.util.Timeout
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.app.Harness
import com.webtrends.harness.app.HarnessActor.ShutdownSystem
import com.webtrends.harness.component._
import com.webtrends.harness.component.messages.{ClusterState, Rejoin, StatusRequest, Subscriptions}
import com.webtrends.harness.component.spray.route.{RouteAccessibility, RouteManager}
import com.webtrends.harness.health._
import com.webtrends.harness.service.ServiceManager
import com.webtrends.harness.service.ServiceManager.GetMetaDataByName
import com.webtrends.harness.service.messages.GetMetaData
import com.webtrends.harness.service.meta.ServiceMetaData
import org.json4s._
import org.joda.time.{DateTime, DateTimeZone}

import scala.util.{Failure, Success}

class InternalSprayWorker extends CoreSprayWorker {
  import context.dispatcher
  implicit val timeout = Timeout(spSettings.requestTimeout.toMillis)

  val healthActor = actorRefFactory.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = actorRefFactory.actorSelection(HarnessConstants.ServicesFullName)

  override def getRoutes: Route = {
    val serviceRoutes = RouteManager.getRoutes(RouteAccessibility.INTERNAL).filter(r => !r.equals(Map.empty))
    (serviceRoutes ++ List(this.baseRoutes, this.staticRoutes)).reduceLeft(_ ~ _)
  }


  override def baseRoutes = {
    get {
      path("favicon.ico") {
        complete(StatusCodes.NoContent)
      } ~
        path("ping") {
          respondPlain {
            complete("pong: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
          }
        } ~
        cidrFilter {
          pathPrefix("healthcheck") {
            path("lb") {
              respondPlain {
                complete((healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String])
              }
            } ~
              path("nagios") {
                //time(nagiosHealthTimer) {
                respondPlain {
                  complete((healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String])
                }
                //}
              } ~
              path("full") {
                //time(healthTimer) {
                respondJson {
                  complete((healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth])
                }
                //}
              }

          } ~
            path("metrics") {
              respondJson {
                ctx =>
                  componentRequest[StatusRequest, JValue]("wookiee-metrics", ComponentRequest(StatusRequest())) onComplete {
                    case Success(s) => ctx.complete(s.resp)
                    case Failure(f) => ctx.failWith(f)
                  }
              }
            } ~
            pathPrefix("services") {
              pathEnd {
                respondJson {
                  complete((serviceActor ? GetMetaData(None)).mapTo[Seq[ServiceMetaData]])
                }
              } ~
                path(Segment) {
                  (service) =>
                    respondJson {
                      complete((serviceActor ? GetMetaDataByName(service)).mapTo[ServiceMetaData])
                    }
                }
            } ~
            pathPrefix("cluster") {
              pathEnd {
                respondJson {
                  ctx =>
                    val req = ComponentRequest(ClusterState(), Some("cluster"))
                    componentRequest[ClusterState, JValue]("wookiee-cluster", req) onComplete {
                      case Success(s) => ctx.complete(s.resp)
                      case Failure(f) => ctx.failWith(f)
                    }
                }
              } ~
                path("discovery") {
                  respondJson {
                    ctx =>
                      componentRequest[Subscriptions, JValue]("wookiee-cluster", ComponentRequest(Subscriptions())) onComplete {
                        case Success(s) => ctx.complete(s.resp)
                        case Failure(f) => ctx.failWith(f)
                      }
                  }
                }
            }
        }
    } ~
      post {
        cidrFilter {
          pathPrefix("services") {
            path(Segment / "restart") {
              (service) =>
                respondPlain {
                  ctx =>
                    serviceActor ! ServiceManager.RestartService(service)
                    ctx.complete(s"The service $service has been asked to restart")
                }
            }
          } ~
            path("shutdown") {
              respondPlain {
                ctx =>
                  ctx.complete("The system is being shutdown: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                  context.parent ! ShutdownSystem
              }
            } ~
            path("restart") {
              respondPlain {
                ctx =>
                  ctx.complete("The actor system is being restarted: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                  Harness.restartActorSystem
              }
            } ~
            pathPrefix("cluster") {
              path("rejoin") {
                respondPlain {
                  ctx =>
                    message("wookiee-cluster", ComponentMessage(Rejoin(true), Some("cluster")))
                    ctx.complete("The cluster is being rejoined: ".concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString))
                }
              }
            }
        }
      }
  }

  def staticRoutes = {
    val rootPath = context.system.settings.config.getString(SprayManager.KeyStaticRoot)
    context.system.settings.config.getString(SprayManager.KeyStaticType) match {
      case "file" =>
        getFromBrowseableDirectory(rootPath)
      case "jar" =>
        getFromResourceDirectory(rootPath)
      case _ =>
        getFromResourceDirectory(rootPath)
    }
  }

}
