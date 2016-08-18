/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webtrends.harness.component.spray

import java.util.concurrent.atomic.AtomicInteger

import com.webtrends.harness.app.HarnessActor.SystemReady
import com.webtrends.harness.component.spray.client.SprayClient
import com.webtrends.harness.component.{ComponentStarted, Component}
import com.webtrends.harness.utils.ConfigUtil
import spray.can.server.ServerSettings

case class HttpRunning()
case class WebSocketRunning()

class SprayManager(name:String) extends Component(name)
    with SprayHttpServer
    with SprayClient
    with SprayWebSocketServer {

  val spSettings = ServerSettings(config)
  val internalHttpPort = ConfigUtil.getDefaultValue(s"$name.http-port", config.getInt, 8080)

  val externalHttpPortPath: String = s"$name.http-external-port"
  val externalHttpPort = config.hasPath(externalHttpPortPath) match {
    case true => Some(config.getInt(externalHttpPortPath))
    case false => None
  }

  val websocketPort = ConfigUtil.getDefaultValue(s"$name.websocket-port", config.getInt, 8081)
  var rCount = new AtomicInteger(0)


  override def receive = super.receive orElse {
    case HttpRunning =>
      checkRunning()
    case WebSocketRunning =>
      checkRunning()
    case HttpReloadRoutes =>
      sendHttpServerMessage(HttpReloadRoutes)
  }

  private def expectedRunningCount = {
    externalHttpPort match {
      case None => 2
      case Some(_) => 3
    }
  }

  private def checkRunning() = {
    rCount.getAndIncrement()
    if (rCount.get() == expectedRunningCount) {
      sendHttpServerMessage(HttpStartProcessing)
      context.parent ! ComponentStarted(self.path.name)
    }
  }

  override def start = {
    startSprayServer(internalHttpPort, externalHttpPort, Some(spSettings))
    startWebSocketServer(websocketPort, Some(spSettings))
    // start the HttpClient actor
    startSprayClient
  }

  override def stop = {
    super.stop
    sendHttpServerMessage(ShutdownServer)
    stopWebSocketServer
  }
}

object SprayManager {
  def ComponentName = "wookiee-spray"

  def KeyHttpClientTimeout = s"$ComponentName.client.timeout"

  def KeyStaticRoot = s"$ComponentName.static-content.root-path"
  def KeyStaticType = s"$ComponentName.static-content.type"
}