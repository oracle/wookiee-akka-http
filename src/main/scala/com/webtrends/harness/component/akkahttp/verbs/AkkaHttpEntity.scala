package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase

trait AkkaHttpEntity[EntityT <: AnyRef] extends AkkaHttpBase {
  this: BaseCommand =>

  def ev: Manifest[EntityT]

  def unmarshaller: FromRequestUnmarshaller[EntityT] = AkkaHttpBase.unmarshaller[EntityT](ev)

  def maxSizeBytes: Long = 1.024e6.toLong // 1MB

  override def beanDirective(bean: CommandBean): Directive1[CommandBean]  =
    (withSizeLimit(maxSizeBytes) & entity(as[EntityT](unmarshaller))).flatMap { entity =>
      bean.addValue(AkkaHttpEntity.Entity, entity)
      super.beanDirective(bean)
    }
}

object AkkaHttpEntity {
  val Entity = "entity"
}
