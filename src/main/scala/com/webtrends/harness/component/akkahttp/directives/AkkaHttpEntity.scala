package com.webtrends.harness.component.akkahttp.directives

import akka.http.javadsl.server.RequestEntityExpectedRejection
import akka.http.scaladsl.model.{HttpMethod, HttpMethods}
import akka.http.scaladsl.server.{Directive, Directive0, Directive1, RequestEntityExpectedRejection}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import akka.http.scaladsl.server.Directives.{entity, _}

trait AkkaHttpEntity[EntityT <: AnyRef] extends AkkaHttpBase {
  this: BaseCommand =>

  def ev: Manifest[EntityT]

  def unmarshaller: FromRequestUnmarshaller[EntityT] = AkkaHttpBase.unmarshaller[EntityT](ev, fmt = formats)

  def maxSizeBytes: Long = 1.024e6.toLong // 1MB

  override def beanDirective(bean: CommandBean, pathName: String = "",
                             method: HttpMethod = HttpMethods.GET): Directive1[CommandBean] = {
    withSizeLimit(maxSizeBytes) & readEntity(bean) & super.beanDirective(bean, pathName, method)
  }

  def readEntity(bean: CommandBean): Directive[Unit] = {
    entity(as[EntityT](unmarshaller)).flatMap { entity =>
      bean.addValue(AkkaHttpEntity.Entity, entity)
      pass
    } recover { rejections =>
      if (rejections.size == 1 && rejections.head.isInstanceOf[RequestEntityExpectedRejection]) {
        pass
      } else reject(rejections :_*)
    }
  }

  // Can use to get the entity that was unmarshalled and put on the bean, will be None if
  // empty payload was passed
  def getEntity[T](bean: CommandBean): Option[T] = {
    bean.getValue[T](AkkaHttpEntity.Entity)
  }
}

object AkkaHttpEntity {
  val Entity = "entity"
}
