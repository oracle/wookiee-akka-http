package com.webtrends.harness.component.spray.routes

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.webtrends.harness.command.CommandBean
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{SprayStreamResponse, RouteManager}
import org.json4s.JsonAST.JObject
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.{HttpHeaders, StatusCodes}
import spray.routing.{HttpService, Directives}
import spray.testkit.Specs2RouteTest


import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class SprayStreamingTestCommand(streamSize: Long, chunkBytes: Int, maxStreamBytes: Long, cmdName: String) extends BaseTestCommand {
  import SprayStreamingTestCommand._
  override def commandName = cmdName
  override def path = s"/$cmdName"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {

    val inputStream = getStream(streamSize)
    Future (new SprayCommandResponse[T](
      Some(SprayStreamResponse(inputStream, Some(maxStreamBytes), chunkBytes).asInstanceOf[T]),
      status = StatusCodes.Accepted,
      additionalHeaders = List (
        HttpHeaders.RawHeader("custom", "header")
      )
    ))
  }
}

object SprayStreamingTestCommand {

  def getStream(size: Long): InputStream = {
    val bytes = new Array[Byte](size.toInt)
    new ByteArrayInputStream(bytes)
  }
}

class SprayStreamingSpecs extends SpecificationWithJUnit
with Directives
with Specs2RouteTest
with HttpService {

  implicit def default = RouteTestTimeout(FiniteDuration(60, TimeUnit.SECONDS))
  def actorRefFactory = system

  "SprayStreamingTestMaxLongAsStreamSize" should {
    "send 100 bytes in 10 chunks" in {
      val name = "SprayStreamingTestMaxLongAsStreamSize"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(20, 10, Long.MaxValue, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 2
        chunks.map(d => d.data.toByteArray.length).sum mustEqual 20
      }
    }
  }

  "SprayStreamingTestSmallDataSize" should {
    "send data back as 1 chunk when byte size is smaller than chunkSize" in {
      val name = "SprayStreamingTestSmallDataSize"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(5, 10, 5, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 1
        chunks.head.data.toByteArray.length mustEqual 5
      }
    }
  }


  "SprayStreamingTestHeaders" should {
    "send proper headers when chunking data" in {
      val name = "SprayStreamingTestHeaders"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(1000, 100, 1000, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 10
        status mustEqual StatusCodes.Accepted
        headers.exists( h => h.name == "custom" && h.value == "header") must beTrue
      }
    }
  }

  "SprayStreamingTestSingleChunk" should {
    "send data back as a single chunk" in {
      val name = "SprayStreamingTestSingleChunk"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(100, 100, 100, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 1
        chunks.head.data.toByteArray.length mustEqual 100
      }
    }
  }

  "SprayStreamingTest10Chunks" should {
    val name = "SprayStreamingTest10Chunks"
    val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(100, 10, 100, name)))
    "send data back as 10 chunks" in {

      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 10
        chunks.map(d => d.data.toByteArray.length).sum mustEqual 100
      }
    }
  }

  "SprayStreamingTestUnevenSize" should {
    "send data back as 10 chunks when not evenly split" in {
      val name = "SprayStreamingTestUnevenSize"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(95, 10, 95, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 10
        chunks.map(d => d.data.toByteArray.length).sum mustEqual 95
        chunks.last.data.toByteArray.length mustEqual 5
      }
    }
  }

  "SprayStreamingTestNoBytes" should {
    "send no data back when stream is empty" in {
      val name = "SprayStreamingTestNoBytes"
      val testCommandRef = system.actorOf(Props(new SprayStreamingTestCommand(0, 10, 10, name)))
      Get(s"/$name") ~> RouteManager.getRoute(s"${name}_get").get ~> check {
        chunks.length mustEqual 0
      }
    }
  }


}


