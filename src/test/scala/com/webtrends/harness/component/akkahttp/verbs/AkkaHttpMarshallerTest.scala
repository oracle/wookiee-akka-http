package com.webtrends.harness.component.akkahttp.verbs

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean, CommandResponse}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import scala.concurrent.Future


class AkkaHttpMarshallerTest extends FunSuite
  with PropertyChecks
  with MustMatchers
  with ScalatestRouteTest {

  test("should marshall entities to JSON by default") {
    val gen = for {
      v0 <- Arbitrary.arbitrary[String]
      v1 <- Arbitrary.arbitrary[Double]
    } yield TestEntity(v0, v1)

    forAll(gen) { entity: TestEntity =>
      var routes = Set.empty[Route]

      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(CommandResponse(Some(entity.asInstanceOf[T])))
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        import TestJsonSupport._
        contentType mustEqual ContentTypes.`application/json`
        entityAs[TestEntity] mustEqual entity
      }
    }
  }

  test("should allow commands to specify custom marshaller") {
    val gen = for {
      v0 <- Gen.alphaStr
      v1 <- Arbitrary.arbitrary[Double]
    } yield TestEntity(v0, v1)

    forAll(gen) { entity: TestEntity =>
      var routes = Set.empty[Route]
      new AkkaHttpGet with TestBaseCommand {
        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          Future.successful(CommandResponse(Some(entity.asInstanceOf[T])))
        }

        override def marshall[T <: AnyRef : Manifest](data: T): ToResponseMarshallable = {
          implicit def m: ToEntityMarshaller[TestEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`text/csv`) { e =>
            s"""
               |v0,v1
               |${e.v0},${e.v1}""".stripMargin
          }

          ToResponseMarshallable(data.asInstanceOf[TestEntity])
        }

      }

      Get("/test") ~> routes.reduceLeft(_ ~ _) ~> check {
        implicit def um: FromEntityUnmarshaller[TestEntity] =
          Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypes.`text/csv(UTF-8)`).map { str =>
            val values = str.split("\n").last.split(",")
            TestEntity(values.head, values.last.toDouble)
          }

        contentType mustEqual ContentTypes.`text/csv(UTF-8)`
        entityAs[TestEntity] mustEqual entity
      }
    }
  }
}
