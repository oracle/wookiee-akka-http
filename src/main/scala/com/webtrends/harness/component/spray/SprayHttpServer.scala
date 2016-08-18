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

import akka.actor.{ActorRef, Actor}
import spray.can.server.ServerSettings

/**
 * @author Michael Cuthbert on 11/17/14.
 */
trait SprayHttpServer { this : Actor =>
  var internalHttpServer:Option[ActorRef] = None
  var externalHttpServer:Option[ActorRef] = None


  def startSprayServer(internalPort:Int, externalPort: Option[Int], settings:Option[ServerSettings]=None)  = {
    internalHttpServer = Some(context.actorOf(CoreSprayServer.props[InternalSprayWorker](internalPort, settings), SprayHttpServer.SprayInternalServerName))
    externalPort match {
      case None =>
      case Some(p) =>
        externalHttpServer = Some(context.actorOf(CoreSprayServer.props[CoreSprayWorker](p, settings), SprayHttpServer.SprayExternalServerName))
    }
    sendHttpServerMessage(BindServer)
  }


  def sendHttpServerMessage(message: Any) = {
    internalHttpServer match {
      case None =>
      case Some(s) => s ! message
    }

    externalHttpServer match {
      case None =>
      case Some(s) => s ! message
    }
  }
}

object SprayHttpServer {
  val SprayInternalServerName = "spray-internal-server"
  val SprayExternalServerName = "spray-external-server"
}
