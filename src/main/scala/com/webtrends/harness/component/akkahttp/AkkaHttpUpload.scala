package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.directives.PathDirectives
import akka.http.scaladsl.server.directives.FileUploadDirectives.uploadedFile
import com.webtrends.harness.command.Command


trait AkkaHttpUpload extends AkkaHttpPost {
  this: Command =>

  addRoute(PathDirectives.path(path) {
    uploadedFile("csv") {
      case (metadata, file) =>
        commandDirective().tapply(_._1)
    }
  })
}