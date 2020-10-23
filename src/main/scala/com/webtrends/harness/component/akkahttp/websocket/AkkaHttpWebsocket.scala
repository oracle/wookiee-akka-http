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

import java.util.concurrent.atomic.AtomicBoolean

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Supervision.{Directive, Stop}
import akka.stream.scaladsl.{Compression, Flow, Keep}
import akka.stream.{ActorAttributes, Materializer}
import akka.util.ByteString
import com.webtrends.harness.component.akkahttp.routes.{AkkaHttpEndpointRegistration, AkkaHttpRequest, EndpointOptions}
import com.webtrends.harness.component.akkahttp.websocket.AkkaHttpWebsocket._
import com.webtrends.harness.logging.LoggingAdapter

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

object AkkaHttpWebsocket {
  case class CompressionType(algorithm: String, flow: Flow[ByteString, ByteString, NotUsed])
  val supportedCompression = Map("gzip" -> Compression.gzip, "deflate" -> Compression.deflate)

  def chosenCompression(headers: Map[String, String]): Option[CompressionType] = {
    headers.map(h => h._1.toLowerCase -> h._2).get("accept-encoding").flatMap { encoding =>
      val encSet = encoding.split(",").map(_.trim).toSet
      val selected = supportedCompression.keySet.intersect(encSet).headOption
      selected.map(algo => CompressionType(algo, supportedCompression(algo)))
    }
  }
}

class AkkaHttpWebsocket[I: ClassTag, O <: Product : ClassTag, A <: Product : ClassTag](
  authHolder: A,
  inputHandler: (A, TextMessage) => Future[I],
  businessLogic: I => Future[O],
  responseHandler: O => TextMessage,
  onClose: A => Unit = {_: A => ()},
  errorHandler: PartialFunction[Throwable, Directive] = AkkaHttpEndpointRegistration.wsErrorDefaultHandler,
  options: EndpointOptions = EndpointOptions.default)(implicit ec: ExecutionContext, mat: Materializer) extends LoggingAdapter {
  var closed: AtomicBoolean = new AtomicBoolean(false)

  // This the the main method to route WS messages
  def websocketHandler(req: AkkaHttpRequest): Flow[Message, Message, Any] = {
    val compressFlow: Flow[TextMessage, Message, NotUsed] = chosenCompression(req.requestHeaders) match {
      case Some(compressOpt) =>
        Flow[TextMessage].map { tx =>
          ByteString(tx.getStrictText)
        }
        .via(compressOpt.flow)
        .map(tx => BinaryMessage(tx))
      case None =>
        Flow[Message]
    }

    Flow[Message]
      .mapAsync(1) {
        case tm: TextMessage =>
          for {
            input <- tryWrap(inputHandler(authHolder, tm))
            result <- tryWrap(businessLogic(input))
          } yield {
            responseHandler(result)
          }
      }
      .viaMat(compressFlow)(Keep.left)
      .withAttributes(ActorAttributes.supervisionStrategy(onError))
      .watchTermination() { (_, done) =>
        done.map { _ =>
          close(authHolder)
        }
      }
  }

  private def close(authInfo: A): Unit =
    if (!closed.getAndSet(true))
      onClose(authInfo)

  private def tryWrap[T](input: Future[T]): Future[T] =
    Try(input).recover({ case err: Throwable =>
      Future.failed[T](err) }).get

  private def onError: PartialFunction[Throwable, Directive] = {
    case err: Throwable =>
      if (errorHandler.isDefinedAt(err)) {
        errorHandler(err) match {
          case Stop =>
            close(authHolder)
            Stop
          case status => status // We'll continue processing
        }
      } else {
        log.warn("Encountered error not covered in 'wsErrorHandler', stopping stream", err)
        Stop
      }
  }
}