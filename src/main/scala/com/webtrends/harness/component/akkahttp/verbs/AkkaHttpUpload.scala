package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileUploadDirectives.uploadedFile
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.CommandLike


trait AkkaHttpUpload extends AkkaHttpBase {
  this: CommandLike =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route =
    uploadedFile("csv") { case (fileInfo, file) =>
      bean.addValue("file", file)
      bean.addValue("file-info", fileInfo)
      super.commandInnerDirective(bean)
  }
}