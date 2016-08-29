package com.webtrends.harness.component.spray.command

import com.webtrends.harness.command.{BaseCommandResponse, CommandResponse}
import spray.http.{StatusCodes, HttpHeader, StatusCode}

case class SprayCommandResponse[T](override val data: Option[T],
                              override val responseType: String = "json",
                              status: StatusCode = StatusCodes.OK,
                              additionalHeaders: List[HttpHeader] = List()
                             ) extends BaseCommandResponse[T]
