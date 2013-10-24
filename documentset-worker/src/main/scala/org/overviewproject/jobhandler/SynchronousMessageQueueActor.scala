package org.overviewproject.jobhandler

import scala.language.postfixOps
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.Exception.allCatch
import scala.util.{ Failure, Success }
import akka.actor._
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.util.Logger
import scala.util.Try
import org.overviewproject.jobhandler.MessageQueueActorProtocol.RegisterWith
import org.overviewproject.messagequeue.ConnectionMonitorProtocol._

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
   
   private def deliverMessage(message: MessageContainer): Unit = self ! message
}
