/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.akkahttp

import com.webtrends.harness.component.Component

case class AkkaHttpMessage()

class AkkaHttpManager(name:String) extends Component(name) with AkkaHttp {
  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive = super.receive orElse {
    case AkkaHttpMessage => println("DO SOMETHING HERE")
  }

  /**
   * Start function will start any child actors that will be managed by the ComponentManager
    *
    * @return
   */
  override def start = {
    startAkkaHttp()
    super.start
  }

  /**
   * Stop will execute any cleanup work to be done for the child actors
   * if not necessary this can be deleted
    *
    * @return
   */
  override def stop = {
    stopAkkaHttp()
    super.stop
  }

}