package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileUploadDirectives.uploadedFile
import com.webtrends.harness.command.{CommandBean, BaseCommand}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase


trait AkkaHttpUpload extends AkkaHttpBase {
  this: BaseCommand =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route =
    uploadedFile("csv") { case (fileInfo, file) =>
      bean.addValue("file", file)
      bean.addValue("file-info", fileInfo)
      super.commandInnerDirective(bean)
  }
}