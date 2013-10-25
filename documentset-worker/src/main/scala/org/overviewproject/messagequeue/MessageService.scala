package org.overviewproject.messagequeue

import javax.jms.Connection


trait MessageContainer {
  val text: String
}

trait MessageService {
  def listenToConnection(connection: Connection, messageDelivery: MessageContainer => Unit): Unit
  def acknowledge(message: MessageContainer): Unit
  def stopListening: Unit
}

