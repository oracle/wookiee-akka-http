/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.component.akkahttp.websocket

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server._
import akka.stream.Materializer
import akka.stream.scaladsl.{Compression, Flow, Keep}
import akka.util.ByteString
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpRequest, EndpointOptions}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class AkkaHttpWebsocket[I: ClassTag, O <: Product : ClassTag, A <: Product : ClassTag](
                        authHolder: A,
                        inputHandler: (A, TextMessage) => Future[I],
                        businessLogic: I => Future[O],
                        responseHandler: O => TextMessage,
                        onClose: A => Unit,
                        errorHandler: AkkaHttpRequest => PartialFunction[Throwable, Route],
                        options: EndpointOptions = EndpointOptions.default)(implicit ec: ExecutionContext, mat: Materializer) {
  val supported = Map("gzip" -> Compression.gzip, "deflate" -> Compression.deflate)

  // This the the main method to route WS messages
  def websocketHandler(req: AkkaHttpRequest): Flow[Message, Message, Any] = {
    val compressFlow: Flow[TextMessage, TextMessage, NotUsed] = req.requestHeaders.get("accept-encoding") match {
      case Some(encoding) =>
        val compressOpt = supported.keySet.intersect(encoding.split(",").map(_.trim).toSet).headOption

        compressOpt.map {
          key => Flow[TextMessage].map(tx => ByteString(tx.getStrictText)).via(supported(key)).map(tx => TextMessage(tx.mkString))
        } getOrElse Flow[TextMessage]
      case None =>
        Flow[TextMessage]
    }

    Flow[Message]
      .mapAsync(1) {
        case tm: TextMessage =>
          for {
            input <- inputHandler(authHolder, tm)
            result <- businessLogic(input)
          } yield {
            responseHandler(result)
          }
      }
      .viaMat(compressFlow)(Keep.left)
      .watchTermination() { (_, done) =>
        done.map { _ =>
          onClose(authHolder)
        }
      }
  }
}