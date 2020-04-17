package com.webtrends.harness.component.akkahttp.util

case class TestEntity(v0: String, v1: Double)

case class ErrorEntity(error: String)

case class RequestInfo(path: String,
                       verb: String,
                       headers: Map[String, String],
                       routeParams: Map[String, String],
                       queryParams: Map[String, String],
                       body: Option[String])