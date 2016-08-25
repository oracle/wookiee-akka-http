package com.webtrends.harness.component.akkahttp

import akka.Done
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Framing, Sink}
import akka.util.ByteString

import scala.concurrent.Future


trait AkkaHttpPost {

  // breaks incoming stream into "frames" (chunks) by buffering until delimiter
  val splitLines = Framing.delimiter(ByteString("\n"), 256)

  val csvUploads =
    path("metadata" / LongNumber) { id =>
      entity(as[Multipart.FormData]) { formData =>
        // mapAsync allows parallelism specified by #
        val done: Future[Done] = formData.parts.mapAsync(1) {
          case b: BodyPart if b.filename.exists(_.endsWith(".csv")) =>
            b.entity.dataBytes
              .via(splitLines)
              // maybe track erroneous lines here
              .map(_.utf8String.split(",").toVector)
              .runForeach(csv =>
                // do something with the data here ###
                metadataActor ! MetadataActor.Entry(id, csv))
          case _ => Future.successful(Done)
        }.runWith(Sink.ignore)

        // when processing have finished create a response for the user
        onSuccess(done) { _ =>
          complete {
            // generate any response to the user
            "ok!"
          }
        }
      }
    }
}
