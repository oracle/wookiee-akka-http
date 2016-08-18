/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.route

import com.webtrends.harness.command.CommandException
import org.json4s.MappingException
import org.slf4j.LoggerFactory
import spray.http.StatusCodes._
import spray.routing.AuthenticationFailedRejection.{CredentialsRejected, CredentialsMissing}
import spray.routing.{AuthenticationFailedRejection, RejectionHandler, Directives, ExceptionHandler}

/**
 * @author Michael Cuthbert on 12/11/14.
 */
trait CommandRouteHandler extends Directives {

  private val externalLogger = LoggerFactory.getLogger(this.getClass)
  implicit val excHandler = ExceptionHandler({
    case ex:Exception =>
      externalLogger.debug(ex.getMessage, ex)
      complete(InternalServerError, "Internal Server Error")
  })

  implicit val rejHandler = RejectionHandler({
    case AuthenticationFailedRejection(cause, authenticator) :: _ =>
      externalLogger.debug(s"Auth failed: cause $cause, $authenticator")
      respondWithHeaders(authenticator) {
        cause match {
          case CredentialsMissing => complete(Unauthorized, "Unauthorized")
          case CredentialsRejected => complete(Unauthorized, "Invalid username/password")
        }
      }
  })

  def debugExceptionHandler = handleExceptions(ExceptionHandler({
    case ce:CommandException =>
      externalLogger.debug(ce.getMessage, ce)
      complete(BadRequest, s"Command Exception - ${ce.getMessage}\n\t${ce.toString}")
    case arg:IllegalArgumentException =>
      externalLogger.debug(arg.getMessage, arg)
      complete(BadRequest, s"Illegal Arguments - ${arg.getMessage}\n\t${arg.toString}")
    case me:MappingException =>
      externalLogger.debug(me.getMessage, me)
      complete(BadRequest, s"Mapping Exception - ${me.getMessage}\n\t${me.toString}")
    case ex:Exception =>
      externalLogger.debug(ex.getMessage, ex)
      complete(InternalServerError, s"Internal Server Error - ${ex.getMessage}\n\t${ex.toString}")
  }))

  def exceptionHandler = handleExceptions(excHandler)

  def rejectionHandler = handleRejections(rejHandler)
}
