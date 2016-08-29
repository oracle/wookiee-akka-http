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

package com.webtrends.harness.component.spray.route


import java.io.InputStream
import java.util.concurrent.TimeUnit
import akka.actor._
import com.webtrends.harness.logging.LoggingAdapter
import spray.can.Http
import spray.http.HttpHeaders.`Content-Length`
import spray.http._
import spray.routing.RequestContext
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

case class SprayStreamResponse(stream: InputStream, maxStreamBytes: Option[Long] = None, chunkBytes: Int = 100000)
case object SendNextChunk
case object BackOffLimitExceeded

class SprayStreamingResponder(response: SprayStreamResponse, context: ActorContext, status: StatusCode) extends LoggingAdapter {
  implicit val executionContext = context.dispatcher

  val buffer = new Array[Byte](response.chunkBytes)
  var remaining = response.maxStreamBytes.getOrElse(Long.MaxValue)

  def respond(ctx: RequestContext): Unit = {

    context.actorOf {
      Props {
        new Actor with ActorLogging {

          val backoffManager = new {
            var backoffMillis = 100
            var attempts = 0

            def reset(): Unit = {
              backoffMillis = 100
              attempts = 0
            }

            def backOff(): Unit = {
              attempts += 1
              backoffMillis *= 2

              if (attempts > 10) {
                self ! BackOffLimitExceeded
              } else {
                context.system.scheduler.scheduleOnce(
                  FiniteDuration(backoffMillis.toLong, TimeUnit.MILLISECONDS), self, SendNextChunk)
              }
            }
          }

          val headers = response.maxStreamBytes match {
            case Some(size) => List(`Content-Length`(size))
            case None => List()
          }
          ctx.responder ! ChunkedResponseStart(HttpResponse(status = status, entity = HttpEntity(""), headers = headers))
            .withAck(SendNextChunk)

          def receive = {

            case SendNextChunk if remaining == 0L =>
              close(ctx.responder)

            case SendNextChunk =>
              readNext() match {
                case Some(-1) => // End of stream
                  close(ctx.responder)

                case Some(0) => // Not expected, but possible depending on InputStream implementation
                  backoffManager.backOff()

                case Some(size) =>
                  backoffManager.reset()
                  ctx.responder ! MessageChunk(buffer.slice(0, size)).withAck(SendNextChunk)

                case None =>
                  close(ctx.responder)
              }

            case BackOffLimitExceeded =>
              log.warning(s"Stopping response streaming due to $BackOffLimitExceeded")
              close(ctx.responder)

            case connectionClosed: Http.ConnectionClosed =>
              log.warning(s"Stopping response streaming due to $connectionClosed")
              close(ctx.responder)
          }


          def close(responder: ActorRef): Unit = {
            responder ! ChunkedMessageEnd
            try {
              response.stream.close()
            }
            catch {
              case NonFatal(nf) => log.error("Failed to close stream", nf)
            }
            finally {
              context.stop(self)
            }
          }

        }
      }
    }
  }

  def readNext(): Option[Int] = {
    try {
      var bytesRead = response.stream.read(buffer, 0, Math.min(buffer.size, remaining).toInt)
      remaining -= bytesRead
      Some(bytesRead)
    }
    catch {
      case NonFatal(nf) =>
        log.error("Unable to read from stream.", nf)
        None
    }
  }
}