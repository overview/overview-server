package org.overviewproject.messagequeue


import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.actor._

import org.overviewproject.messagequeue.ConnectionMonitorProtocol._
import org.overviewproject.messagequeue.AcknowledgingMessageReceiverProtocol._
import org.overviewproject.util.Logger


/**
 * A MessageReceiver is a message queue client, that converts and forwards incoming messages to the 
 * specified `messageRecipient`.
 * If the connection fails, the MessageReceiver handles disconnecting and reconnecting to the new connection.
 */
class MessageReceiver[T](messageRecipient: ActorRef,
                         messageService: MessageService,
                         convertMessage: String => T) extends Actor {
  
   def receive = {
     case RegisterWith(connectionMonitor) => connectionMonitor ! RegisterClient
     case ConnectedTo(connection) => messageService.listenToConnection(connection, deliverMessage)
     case ConnectionFailed => messageService.stopListening
     case message: MessageContainer => {
       Try(convertMessage(message.text)) match {
         case Success(m) => messageRecipient ! m
         case Failure(e) => Logger.error(s"Unable to convert incoming message", e)
       }
     }
   }
   
   /** Callback used by the messageService on incoming messages, which are converted to akka messages */
   private def deliverMessage(message: MessageContainer): Unit = self ! message
}
