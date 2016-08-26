package com.webtrends.harness.component.akkahttp

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn
import akka.Done
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Framing, Sink}
import akka.util.ByteString

import scala.concurrent.Future


object WebServer {
  def main(args: Array[String]) {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      path("file") {
        entity(as[Multipart.FormData]) { formData =>
          val done: Future[Done] = formData.parts.mapAsync(1) {
            case b: BodyPart if b.name == "file" =>
              val file = File.createTempFile("upload", "tmp")
              b.entity.dataBytes.runWith(FileIO.toPath(file.toPath)).map(_ => b.name -> file)
            case _ => Future.successful(Done)
          }.runWith(Sink.ignore)

          // when processing have finished create a response for the user
          onSuccess(done) { _ =>
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<!DOCTYPE html><html><body>Processed</body></html>"))
          }
        }
      }


//    val splitLines = Framing.delimiter(ByteString("\n"), 256, true)

//    val route =
//      path("file") {
//        entity(as[Multipart.FormData]) { formData =>
//          val done: Future[Done] = formData.parts.mapAsync(1) {
//            case b: BodyPart if b.filename.exists(_.endsWith(".csv")) =>
//              println(s"${b.filename} file uploaded! ######")
//              b.entity.dataBytes
//                .via(splitLines)
//                .map(_.utf8String.split(",").toVector)
//                .runForeach(csv =>
//                  csv)
//            //                    .runForeach(csv =>
//            //                      metaDataActor ! MetaDataActor.Entity(csv))
//            case b: BodyPart if b.filename.exists(_.endsWith(".csv.gz")) =>
//              println(s"${b.filename} file uploaded! ######")
//              b.entity.dataBytes
//                .via(splitLines)
//                .map(_.utf8String.split(",").toVector)
//                .runForeach(csv =>
//                  csv)
//            case _ => Future.successful(Done)
//          }.runWith(Sink.ignore)
//
//          // when processing have finished create a response for the user
//          onSuccess(done) { _ =>
//            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, s"<!DOCTYPE html><html><body>Processed</body></html>"))
//          }
//        }
//      }


    // `route` will be implicitly converted to `Flow` using `RouteResult.route2HandlerFlow`
    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}