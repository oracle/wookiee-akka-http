package com.webtrends.harness.component.spray.directive

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import akka.testkit.TestActorRef
import com.webtrends.harness.command.{CommandBean, Command}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.route.{RouteManager, SprayPost, SprayGet}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.JObject
import org.specs2.mutable.SpecificationWithJUnit
import spray.http.HttpHeaders._
import spray.http._
import spray.routing.{HttpService, Directives}
import spray.testkit.Specs2RouteTest
import java.util.zip.{GZIPOutputStream, GZIPInputStream}

import scala.concurrent.Future
import scala.io.Source

class CompressResponseCommand extends Command with SprayGet with HttpCompression{
  import context.dispatcher
  override def commandName: String = "CompressResponseCommand"
  override def path: String = "/test/CompressResponseCommand"
  val responseData = new JObject(List())

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {
    Future (new SprayCommandResponse[T](Some(responseData.asInstanceOf[T])))
  }
}

object DecompressRequestCommand {
  implicit val formats = DefaultFormats
  val requestContent = "{\"foo\":\"bar\"}"
  val requestJson = parse(requestContent)
}
class DecompressRequestCommand extends Command with SprayPost with HttpCompression{
  import context.dispatcher
  override def commandName: String = "DecompressRequestCommand"
  override def path: String = "/test/DecompressRequestCommand"

  override def execute[T:Manifest](bean: Option[CommandBean]): Future[SprayCommandResponse[T]] = {

    if(bean.get(CommandBean.KeyEntity).asInstanceOf[JObject].values.contains("foo")) {
      Future (new SprayCommandResponse[T](Some(DecompressRequestCommand.requestJson.asInstanceOf[T])))
    }
    else {
      throw new Exception()
    }
  }
}

class HttpCompressionSpec extends SpecificationWithJUnit
with Directives
with Specs2RouteTest
with HttpService {

  def actorRefFactory = system

  TestActorRef[CompressResponseCommand]
  TestActorRef[DecompressRequestCommand]

  "Commands with HttpCompression trait" should {

    "Compress responses if supported by client" in {
      HttpRequest(
        HttpMethods.GET,
        "/test/CompressResponseCommand",
        List(
          `Accept-Encoding`(HttpEncodings.gzip)
        ),
        None
      ) ~> RouteManager.getRoute("CompressResponseCommand_get").get ~> check {
        status mustEqual StatusCodes.OK
        headers.find(_.name == "Content-Encoding").get.value mustEqual "gzip"

        val zipInputStream = new GZIPInputStream(new ByteArrayInputStream(responseAs[Array[Byte]]))
        val decompressed = Source.fromInputStream(zipInputStream).mkString
        decompressed mustEqual "{}"
      }
    }

    "Not compress responses if not supported by client" in {
      Get("/test/CompressResponseCommand") ~> RouteManager.getRoute("CompressResponseCommand_get").get ~> check {
        status mustEqual StatusCodes.OK
        responseAs[String] mustEqual "{}"
      }
    }

    "decompress requests" in {

      val byteOut = new ByteArrayOutputStream(DecompressRequestCommand.requestContent.length)
      val zipOutputStream = new GZIPOutputStream(byteOut)
      zipOutputStream.write(DecompressRequestCommand.requestContent.getBytes())
      zipOutputStream.close()

      HttpRequest(
        HttpMethods.POST,
        "/test/DecompressRequestCommand",
        List(
          `Content-Encoding`(HttpEncodings.gzip)
        ),
        HttpEntity(ContentTypes.`application/json`, byteOut.toByteArray)
      ) ~> RouteManager.getRoute("DecompressRequestCommand_post").get ~> check {
        status mustEqual StatusCodes.OK
        responseAs[String] mustEqual DecompressRequestCommand.requestContent
      }
    }

    "Not attempt to decompress requests that aren't compressed" in {
      HttpRequest(
        HttpMethods.POST,
        "/test/DecompressRequestCommand",
        List(),
        HttpEntity(ContentTypes.`application/json`, DecompressRequestCommand.requestContent)
      ) ~> RouteManager.getRoute("DecompressRequestCommand_post").get ~> check {
        status mustEqual StatusCodes.OK
        responseAs[String] mustEqual DecompressRequestCommand.requestContent
      }
    }
  }
}
