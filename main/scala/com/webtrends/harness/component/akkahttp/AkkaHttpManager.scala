/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 */
package com.webtrends.harness.component.akkahttp

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.http.scaladsl.settings.ServerSettings
import com.typesafe.config.Config
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.akkahttp.logging.AccessLog
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpUnbind, ExternalAkkaHttpActor, InternalAkkaHttpActor, WebsocketAkkaHttpActor}
import com.webtrends.harness.logging.Logger
import com.webtrends.harness.utils.ConfigUtil

import scala.concurrent.duration._
import scala.util.Try

case class AkkaHttpMessage()

class AkkaHttpManager(name:String) extends Component(name) with AkkaHttp {
  val settings = AkkaHttpSettings(config)
  AccessLog.accessLoggingEnabled = ConfigUtil.getDefaultValue(
    s"${AkkaHttpManager.ComponentName}.access-logging.enabled", config.getBoolean, true)
  if (AccessLog.accessLoggingEnabled) log.info("Access Logging Enabled") else log.info("Access Logging Disabled")
  val starMonitor = new Object()

  var internalAkkaHttpRef: Option[ActorRef] = None
  var externalAkkaHttpRef: Option[ActorRef] = None
  var wsAkkaHttpRef: Option[ActorRef] = None
  implicit val logger: Logger = log

  // Pretty much just notes of previous meetings
//  def addExternalCommand[U <: Product: ClassTag, V: ClassTag](id: String,
//                                                          businessLogic: U => Future[V],
//                                                          customUnmarshaller: Bean => U,
//                                                          customMarshaller: V => Array[Byte],
//                                                          endpointConfig: EndpointConfig)(implicit ec: ExecutionContext): Unit = {
//
//    // TODO: Split based off of type of endpoint type [External|Internal|WS]
//    addEndpoint(endpointConfig.path, businessLogic)
//      .map(ref => ExternalAkkaHttpRouteContainer.addRoute(RouteGenerator.makeRoute(ref, endpointConfig, customMarshaller)))
//  }

//  class Endpoint[U, V](endpointConfig: EndpointConfig, businessLogic: U => Future[V])
//  class AkkaHttpEndpointConfig(path: String, method: ...) extends EndpointConfig {
//
//  }
//
//  def getPrincipal(request: AkkaHttpRequest): Principal
//  def getPayloadType[T](request: AkkaHttpRequest): T
//  def newPayloadHandler[T](request: AkkaHttpRequest): Either[ApiException, (Principal, T)] = {
//    // get principal
//    // can principal perform action on this request.path?
//    // yes:
//      Right((getPrincipal(bean), getPayloadType[T](bean)))
//    // no:
//     Left(NotAuthorizedException)
//  }
//
//  def makeGroup(requestObjs: (Principal, Group)): Future[Either[Exception, Group]]
////  Endpoint(AkkaHttpEndpointConfig("/groups", HttpMethod.POST, principalUnmarshaller, groupsMarshaller), getGroups)
//  addExternalHttpEndpoint(newPayloadHandler[Group], makeGroup, groupsMarshaller, "/groups", HttpMethods.POST)
//  addGrpcEndpoint(grpcPrincipalUnmarshaller, getGroups, grpcGroupsMarshaller)
//  addZkEndpoint(requestHandler, businessLogic, responseHandler)
//
//  @Method("GET")
//  val config = AkkaHttpEndpointConfig(....)

  def startAkkaHttp(): Unit = {
    starMonitor.synchronized {
      log.info("Starting Wookiee Akka HTTP Actors...")
      internalAkkaHttpRef = Some(context.actorOf(InternalAkkaHttpActor.props(settings.internal), AkkaHttpManager.InternalAkkaHttpName))
      if (settings.external.enabled) {
        externalAkkaHttpRef = Some(context.actorOf(ExternalAkkaHttpActor.props(settings.external), AkkaHttpManager.ExternalAkkaHttpName))
      }
      if (settings.ws.enabled) {
        wsAkkaHttpRef = Some(context.actorOf(WebsocketAkkaHttpActor.props(settings.ws), AkkaHttpManager.WebsocketAkkaHttpName))
      }
      log.info("Wookiee Akka HTTP Actors Ready, Request Line is Open!")
    }
  }

  def stopAkkaHttp(): Unit = {
    Seq(internalAkkaHttpRef, externalAkkaHttpRef, wsAkkaHttpRef).flatten.foreach(_ ! AkkaHttpUnbind)
  }

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive: PartialFunction[Any, Unit] = super.receive orElse {
    case AkkaHttpMessage => println("DO SOMETHING HERE")
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
    *
    * @return
   */
  override def start: Unit = {
    startAkkaHttp()
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
    *
    * @return
   */
  override def stop: Unit = {
    stopAkkaHttp()
    super.stop
  }

}

object AkkaHttpManager {
  val ComponentName = "wookiee-akka-http"

  def KeyStaticRoot = s"$ComponentName.static-content.root-path"
  def KeyStaticType = s"$ComponentName.static-content.type"

  val ExternalAkkaHttpName = "ExternalAkkaHttp"
  val InternalAkkaHttpName = "InternalAkkaHttp"
  val WebsocketAkkaHttpName = "WebsocketAkkaHttp"
}

final case class InternalAkkaHttpSettings(interface: String, port: Int, serverSettings: ServerSettings)
final case class ExternalAkkaHttpSettings(enabled: Boolean, interface: String, port: Int,
                                          serverSettings: ServerSettings)
final case class WebsocketAkkaHttpSettings(enabled: Boolean, interface: String, port: Int,
                                           serverSettings: ServerSettings, keepAliveFrequency: FiniteDuration, keepAliveOn: Boolean)
final case class AkkaHttpSettings(internal: InternalAkkaHttpSettings, external: ExternalAkkaHttpSettings,
                                  ws: WebsocketAkkaHttpSettings)

object AkkaHttpSettings {
  def apply(config: Config): AkkaHttpSettings = {
    val internalPort = ConfigUtil.getDefaultValue("wookiee-akka-http.internal-server.http-port", config.getInt, 8080)
    val internalInterface = ConfigUtil.getDefaultValue("wookiee-akka-http.internal-server.interface", config.getString, "0.0.0.0")

    val externalServerEnabled = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.external-server.enabled", config.getBoolean, false)
    val externalPort = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.external-server.http-port", config.getInt, 8082)
    val externalInterface = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.external-server.interface", config.getString, "0.0.0.0")
    val wsEnabled = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.websocket-server.enabled", config.getBoolean, false)
    val wsPort = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.websocket-server.port", config.getInt, 8081)
    val wsInterface = ConfigUtil.getDefaultValue(
      s"${AkkaHttpManager.ComponentName}.websocket-server.interface", config.getString, "0.0.0.0")
    // How often to send a keep alive heartbeat message back
    val keepAliveFrequency: FiniteDuration = Try(config.getDuration(
      s"${AkkaHttpManager.ComponentName}.websocket-keep-alives.interval", TimeUnit.SECONDS).toInt).getOrElse(30) seconds
    val keepAliveOn: Boolean = Try(config.getBoolean(
      s"${AkkaHttpManager.ComponentName}.websocket-keep-alives.enabled")).getOrElse(false)
    val serverSettings = ServerSettings(config)


    AkkaHttpSettings(
      InternalAkkaHttpSettings(internalInterface, internalPort, serverSettings),
      ExternalAkkaHttpSettings(externalServerEnabled, externalInterface, externalPort, serverSettings),
      WebsocketAkkaHttpSettings(wsEnabled, wsInterface, wsPort, serverSettings, keepAliveFrequency, keepAliveOn)
    )
  }
}