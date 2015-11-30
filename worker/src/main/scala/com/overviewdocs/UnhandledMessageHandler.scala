package com.overviewdocs

import akka.actor.{Actor,Props,UnhandledMessage}

import com.overviewdocs.util.Logger

class UnhandledMessageHandler extends Actor {
  private val logger = Logger.forClass(getClass)

  override def receive = {
    case UnhandledMessage(message, sender, recipient) => {
      logger.error("Unhandled message from {} to {}: {}", sender, recipient, message)
      if (message.isInstanceOf[Exception]) {
        message.asInstanceOf[Exception].printStackTrace()
      }
      Runtime.getRuntime.exit(1)
    }
  }
}

object UnhandledMessageHandler {
  def props: Props = Props(new UnhandledMessageHandler)
}
