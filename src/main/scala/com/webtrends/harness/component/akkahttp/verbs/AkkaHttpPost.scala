package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.component.akkahttp.AkkaHttpBase
import com.webtrends.harness.component.akkahttp.AkkaHttpBase.CommandLike
import de.heikoseeberger.akkahttpjson4s.Json4sSupport._
import org.json4s.JObject

trait AkkaHttpPost extends AkkaHttpBase {
  this: CommandLike =>

  override protected def commandInnerDirective[T <: AnyRef : Manifest](bean: CommandBean): Route = post {
    entity(as[JObject]) { e =>
      bean.addValue(AkkaHttpBase.Entity, e)
      super.commandInnerDirective(bean)
    }
  }

}
