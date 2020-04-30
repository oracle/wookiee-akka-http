/*
 *  Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webtrends.harness.component.akkahttp.util

import akka.actor.Props
import com.webtrends.harness.command.Command

import scala.concurrent.Future
import scala.reflect.ClassTag

object TestCommandActor {

  def createCommandActor[T <: Product : ClassTag, U <: Any : ClassTag](businessLogic: T => Future[U]): Props = {
    class CommandActor extends Command[T, U] {
      override def execute(input: T): Future[U] = {
        businessLogic(input)
      }
    }
    Props(new CommandActor())
  }

}
