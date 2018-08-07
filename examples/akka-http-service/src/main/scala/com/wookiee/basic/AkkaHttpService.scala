package com.wookiee.basic

import com.webtrends.harness.service.Service
import com.wookiee.basic.command.AkkaHttpReadCommand

class AkkaHttpService extends Service {
  override def addCommands: Unit = {
    addCommand(AkkaHttpReadCommand.CommandName, classOf[AkkaHttpReadCommand])
  }
}
