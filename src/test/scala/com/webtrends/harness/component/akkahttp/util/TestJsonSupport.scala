package com.webtrends.harness.component.akkahttp.util

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

object TestJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val testEntityFormat = jsonFormat2(TestEntity)
  implicit val errorEntityFormat = jsonFormat1(ErrorEntity)
}
