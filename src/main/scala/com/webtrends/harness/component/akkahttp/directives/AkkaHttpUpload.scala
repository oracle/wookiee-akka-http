package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.webtrends.harness.command.{Command, MapBean}
import com.webtrends.harness.component.akkahttp.{AkkaHttpBase, AkkaHttpCommandResponse}


trait AkkaHttpUpload extends AkkaHttpBase {
  this: Command[MapBean, AkkaHttpCommandResponse[_]] =>

  def fileField: String = "file"
  def maxFileSizeBytes: Long = 2.5e8.toLong

  override def beanDirective(bean: MapBean, pathName: String = "", method: HttpMethod = HttpMethods.GET): Directive1[MapBean] =
    (withSizeLimit(maxFileSizeBytes) & fileUpload(fileField)).flatMap { case (fileInfo: FileInfo, fileStream: Source[ByteString, Any]) =>
      bean.addValue(AkkaHttpUpload.FileInfo, fileInfo)
      bean.addValue(AkkaHttpUpload.FileStream, fileStream)
      super.beanDirective(bean, pathName, method)
    }
}

object AkkaHttpUpload {
  val FileInfo = "file-info"
  val FileStream = "file-stream"
}