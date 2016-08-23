package com.webtrends.harness.component.akkahttp

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.PathDirectives
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean}

import scala.util.{Failure, Success}

case class AkkaHttpCommandResponse(data: Option[ToResponseMarshallable], responseType: String = "_")
  extends BaseCommandResponse[ToResponseMarshallable]

trait AkkaHttpGet {
  this: Command =>

  protected def executeRoute[T<:AnyRef:Manifest](bean:Option[CommandBean]=None) = {
    onComplete[BaseCommandResponse[T]](execute[T](None).mapTo[BaseCommandResponse[T]]) {
      case Success(AkkaHttpCommandResponse(Some(data), _)) => complete(data)
      case Success(_) => failWith(new Exception("foo"))
      case Failure(f) => failWith(f)
    }
  }

  def route = PathDirectives.path(path){get{ executeRoute()}}
}
