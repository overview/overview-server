package org.overviewproject.messagequeue

import javax.jms.Connection
import scala.language.postfixOps
import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import akka.actor._
import org.overviewproject.messagequeue.MessageHandlerProtocol._


object AcknowledgingMessageReceiverFSM {
  sealed trait State
  case object MessageHandlerIsIdle extends State
  case object MessageHandlerIsBusy extends State
  case object IgnoreMessageHandler extends State
  
  sealed trait Data {
    val messageHandler: ActorRef
  }
  
  case class MessageHandler(messageHandler: ActorRef) extends Data
  case class Task(messageHandler: ActorRef, message: MessageContainer) extends Data
  case class Reconnected(messageHandler: ActorRef, connection: Connection) extends Data
}

object AcknowledgingMessageReceiverProtocol {
  case class RegisterWith(connectionMonitor: ActorRef)
}


trait MessageHandling[T] {
  def createMessageHandler: Props
  def convertMessage(message: String): T
}


import AcknowledgingMessageReceiverFSM._

/**
 * An AcknowledgingMessageReceiver is a message queue client that creates a message handler, converts and 
 * forwards incoming messages to the handler, and waits for a response from the handler before
 * acknowledging the message.
 * If the message queue is setup to use message groups, the AcknowledgingMessageReceiver ensures that
 * messages for a specific document set are sent sequentially to the same message handler.
 * If the connection fails while the message handler is processing a message,
 * the receiver ignores any status from the message handler, and starts forwarding messages when the 
 * connection is re-established and the message handler has completed processing the previous message.
 * The message queue will resend the message, so the message handler must be prepared to receive
 * the same message multiple times.
 */
abstract class AcknowledgingMessageReceiver[T](messageService: MessageService) extends Actor with FSM[State, Data] with MessageHandling[T] {
  import AcknowledgingMessageReceiverProtocol._
  import org.overviewproject.messagequeue.ConnectionMonitorProtocol._

  startWith(MessageHandlerIsIdle, MessageHandler(context.actorOf(createMessageHandler)))

  when(MessageHandlerIsIdle) {
    case Event(RegisterWith(connectionMonitor), _) => {
      connectionMonitor ! RegisterClient
      stay
    }
    case Event(ConnectedTo(connection), _) => {
      messageService.listenToConnection(connection, deliverMessage)
      stay
    }
    case Event(message: MessageContainer, MessageHandler(messageHandler)) => {
      messageHandler ! convertMessage(message.text)
      goto(MessageHandlerIsBusy) using Task(messageHandler, message)
    }
    case Event(ConnectionFailed, _) => {
      messageService.stopListening
      stay
    }
  }
  
  when(MessageHandlerIsBusy) {
    case Event(MessageHandled, Task(messageHandler, message)) => {
      messageService.acknowledge(message)
      
      goto(MessageHandlerIsIdle) using MessageHandler(messageHandler)
    }
    case Event(ConnectionFailed, Task(messageHandler, _)) => {
      messageService.stopListening
      goto(IgnoreMessageHandler) using MessageHandler(messageHandler)
    }
  }
  
  when(IgnoreMessageHandler) {
    case Event(MessageHandled, messageHandler: MessageHandler) => {
      goto(MessageHandlerIsIdle) using messageHandler
    } 
    case Event(MessageHandled, Reconnected(messageHandler, connection)) => {
      self ! ConnectedTo(connection)
      goto(MessageHandlerIsIdle) using MessageHandler(messageHandler)
    }
    case Event(ConnectedTo(connection), MessageHandler(messageHandler)) => {
      stay using Reconnected(messageHandler, connection)
    }
    case Event(ConnectionFailed, data) => { 
      stay using MessageHandler(data.messageHandler)
    }
  }
      
  initialize

  private def deliverMessage(message: MessageContainer): Unit = {
    self ! message
  }
}
