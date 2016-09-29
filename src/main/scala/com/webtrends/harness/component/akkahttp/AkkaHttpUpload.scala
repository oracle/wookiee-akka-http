package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileUploadDirectives.uploadedFile
import com.webtrends.harness.command.{Command, CommandBean}


trait AkkaHttpUpload extends AkkaHttpBase {
  this: Command =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route =
    uploadedFile("csv") { case (fileInfo, file) =>
      bean.addValue("file", file)
      bean.addValue("file-info", fileInfo)
      super.commandInnerDirective(bean)
  }
}