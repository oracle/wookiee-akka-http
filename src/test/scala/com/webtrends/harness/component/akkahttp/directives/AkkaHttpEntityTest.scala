package com.webtrends.harness.component.akkahttp.directives

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{ContentTypes, EntityStreamSizeException, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.server.{MalformedRequestContentRejection, Route, UnsupportedRequestContentTypeRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import com.webtrends.harness.command.{BaseCommandResponse, CommandBean}
import com.webtrends.harness.component.akkahttp.methods.{AkkaHttpPost, AkkaHttpPut}
import com.webtrends.harness.component.akkahttp.util.{ErrorEntity, TestBaseCommand, TestEntity}
import com.webtrends.harness.component.akkahttp.{AkkaHttpCommandResponse, AkkaHttpException}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Inside, MustMatchers}

import scala.concurrent.Future

class AkkaHttpEntityTest extends FunSuite with PropertyChecks with MustMatchers with ScalatestRouteTest with Inside {


  val gen = for {
    v0 <- Arbitrary.arbitrary[String]
    v1 <- Arbitrary.arbitrary[Double]
  } yield TestEntity(v0, v1)

  val gen2 = for {
    v0 <- Gen.alphaStr
    v1 <- Arbitrary.arbitrary[Double]
  } yield TestEntity(v0, v1)

  test("should unmarshall JSON requests to entities by default") {

    forAll(gen) { entity: TestEntity =>

      var routes = Set.empty[Route]

      new AkkaHttpPost with AkkaHttpEntity[TestEntity] with TestBaseCommand {

        override def ev: Manifest[TestEntity] = Manifest.classType(classOf[TestEntity])

        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val e = bean.get(AkkaHttpEntity.Entity).asInstanceOf[TestEntity]
          val response = AkkaHttpCommandResponse(Some(e.asInstanceOf[T]))
          Future.successful(response)
        }
      }


      import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

      Post("/test", content = Some(entity)) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.OK
        contentType mustEqual ContentTypes.`application/json`
        entityAs[TestEntity] mustEqual entity
      }
    }
  }

  test("should honor size limit") {

    forAll(gen) { entity: TestEntity =>

      var routes = Set.empty[Route]
      val size = 1

      new AkkaHttpPost with AkkaHttpEntity[TestEntity] with TestBaseCommand {

        override def ev: Manifest[TestEntity] = Manifest.classType(classOf[TestEntity])

        override def maxSizeBytes: Long = size

        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val e = bean.get(AkkaHttpEntity.Entity).asInstanceOf[TestEntity]
          val response = AkkaHttpCommandResponse(Some(e.asInstanceOf[T]))
          Future.successful(response)
        }
      }

      import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._

      Post("/test", content = Some(entity)) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.BadRequest
      }
    }
  }

  test("should reject non JSON requests") {

    forAll(gen) { entity: TestEntity =>

      var routes = Set.empty[Route]

      new AkkaHttpPut with AkkaHttpEntity[TestEntity] with TestBaseCommand {

        override def ev: Manifest[TestEntity] = Manifest.classType(classOf[TestEntity])

        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val e = bean.get(AkkaHttpEntity.Entity).asInstanceOf[TestEntity]
          val response = AkkaHttpCommandResponse(Some(e.asInstanceOf[T]))
          Future.successful(response)
        }
      }

      implicit val m: ToEntityMarshaller[TestEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`text/csv`) { e =>
        s"""
           |v0,v1
           |${e.v0},${e.v1}""".stripMargin
      }

      Put("/test", content = Some(entity)) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.UnsupportedMediaType
      }
    }
  }

  test("should fail with MalformedRequestContent when unmarhsaller fail") {

    forAll(gen) { entity: TestEntity =>

      var routes = Set.empty[Route]

      new AkkaHttpPut with AkkaHttpEntity[TestEntity] with TestBaseCommand {

        override def ev: Manifest[TestEntity] = Manifest.classType(classOf[TestEntity])

        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val e = bean.get(AkkaHttpEntity.Entity).asInstanceOf[TestEntity]
          val response = AkkaHttpCommandResponse(Some(e.asInstanceOf[T]))
          Future.successful(response)
        }
      }

      implicit val m: ToEntityMarshaller[TestEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`) { e =>
        s"""
           |v0,v1
           |${e.v0},${e.v1}""".stripMargin
      }

      Put("/test", content = Some(entity)) ~> routes.reduceLeft(_ ~ _) ~> check {
        status mustEqual StatusCodes.BadRequest
      }
    }
  }

  test("should allow commands to specify an unmarshaller") {

    forAll(gen2) { entity: TestEntity =>

      val ctype = MediaTypes.`text/csv`
      val responseEntity = ErrorEntity("worked")
      var routes = Set.empty[Route]

      new AkkaHttpPut with AkkaHttpEntity[TestEntity] with TestBaseCommand {

        override def ev: Manifest[TestEntity] = Manifest.classType(classOf[TestEntity])

        override def unmarshaller: FromRequestUnmarshaller[TestEntity] = {
          Unmarshaller.messageUnmarshallerFromEntityUnmarshaller(
            Unmarshaller
              .stringUnmarshaller
              .forContentTypes(ctype)
              .map { data =>
                val values = data.split("\n").last.split(",")
                TestEntity(values.head, values.last.toDouble)
              }
          )
        }

        override def path: String = "test"

        override def addRoute(r: Route): Unit = routes += r

        override def execute[T: Manifest](bean: Option[CommandBean]): Future[BaseCommandResponse[T]] = {
          val e = bean.get(AkkaHttpEntity.Entity).asInstanceOf[TestEntity]
          Future.failed(AkkaHttpException[ErrorEntity](responseEntity, StatusCodes.OK))
        }
      }

       implicit val m: ToEntityMarshaller[TestEntity] = Marshaller.StringMarshaller.wrap(ctype) { e =>
        s"""
           |v0,v1
           |${e.v0},${e.v1}""".stripMargin
      }

      Put("/test", content = Some(entity)) ~> routes.reduceLeft(_ ~ _) ~> check {
        import com.webtrends.harness.component.akkahttp.util.TestJsonSupport._
        status mustEqual StatusCodes.OK
        contentType mustEqual ContentTypes.`application/json`
        entityAs[ErrorEntity] mustEqual responseEntity
      }
    }
  }
}

