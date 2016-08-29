package com.webtrends.harness.component.spray.directive

import com.webtrends.harness.command.Command
import com.webtrends.harness.component.spray.route.SprayRoutes
import spray.routing.Directive0
import spray.routing.directives.{CompressResponseMagnet, RefFactoryMagnet, EncodingDirectives}

trait HttpCompression {this:Command with SprayRoutes =>
  override def compression: Directive0 = {
    decompressRequest() &  EncodingDirectives.compressResponseIfRequested(RefFactoryMagnet.fromUnit((): Unit)(context))
  }
}
