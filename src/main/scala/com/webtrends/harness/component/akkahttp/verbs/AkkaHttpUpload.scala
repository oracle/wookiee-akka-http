package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives.{path => p, _}
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase


trait AkkaHttpUpload extends AkkaHttpBase {
  this: BaseCommand =>

  def fileField: String = "file"
  def maxFileSizeBytes: Long = 2.5e8.toLong

  override def beanDirective(bean: CommandBean): Directive1[CommandBean] =
    (withSizeLimit(maxFileSizeBytes) & fileUpload(fileField)).flatMap { case (fileInfo: FileInfo, fileStream: Source[ByteString, Any]) =>
      bean.addValue(AkkaHttpUpload.FileInfo, fileInfo)
      bean.addValue(AkkaHttpUpload.FileStream, fileStream)
      super.beanDirective(bean)
    }
}

object AkkaHttpUpload {
  val FileInfo = "file-info"
  val FileStream = "file-stream"
}