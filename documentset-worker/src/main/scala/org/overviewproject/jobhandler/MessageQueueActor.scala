package org.overviewproject.jobhandler

import scala.language.postfixOps
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._

import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.util.Logger

import MessageQueueActorFSM._

trait MessageHandling[T] {
  def createMessageHandler: Props
  def convertMessage(message: String): T
}

object MessageQueueActorProtocol {
  case object StartListening
  case class ConnectionFailure(e: Exception)
}

object MessageQueueActorFSM {
  sealed trait State

  case object NotConnected extends State
  case object Ready extends State
  case object WaitingForCompletion extends State

  sealed trait Data
  case object Idle extends Data
  case class Listening(messageHandler: ActorRef) extends Data
  case class ConnectionFailed(e: Throwable) extends Data
}

/**
 * A bridge between message queues and actor messages.
 * Tries to connect to the message queue specified by the mixed in MessageServiceComponent.
 * When a message is received, it's converted to a message object and sent to the message handler
 * specified by the MessageHandling mixin.
 * When the message handler completes, it notifies the MessageQueueActor, which acknowledges the original message
 * queue actor, and listens for a new message. At most one message is being processed at any given time.
 * The message queue message delivery and acknowledgement occurs in a separate thread, so the MessageQueueActor can
 * still receieve akka messages while the MessageServiceComponent is blocked waiting to acknowledge the queue message.
 */
abstract class MessageQueueActor[T](messageService: MessageService) extends Actor with FSM[State, Data] with MessageHandling[T] {

  // Time between reconnection attempts
  private val ReconnectionInterval = 1 seconds
  
  // The communication mechanism between the Actor thread and the MessageServiceComponent thread
  // The MessageServiceComponent blocks waiting for `currentJobCompletion` to complete. The `MessageQueueActor`
  // completes the promise when the message handler has finished its task.
  // FIXME: make `currentJobCompletion` a state variable
  private var currentJobCompletion: Option[Promise[Unit]] = None

  import MessageQueueActorProtocol._

  startWith(NotConnected, Idle)

  when(NotConnected) {
    case Event(StartListening, _) => {
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => {
          goto(Ready) using Listening(context.actorOf(createMessageHandler))
        }
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", StartListening, ReconnectionInterval, repeat = false)
          stay using ConnectionFailed(e)
        }

      }
    }
    case Event(ConnectionFailure, _) => stay // For some reason, and extra event is generated when job is in progress
  }

  when(Ready) {
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
    case Event(message, listener: Listening) => {
      listener.messageHandler ! message
      goto(WaitingForCompletion) using listener
    }
  }

  when(WaitingForCompletion) {
    case Event(MessageHandled, _) => {
      currentJobCompletion.map(_.success())
      currentJobCompletion = None
      goto(Ready)
    }
    case Event(ConnectionFailure(e), _) => {
      Logger.error(s"Connection Failure: ${e.getMessage}", e)
      goto(NotConnected) using ConnectionFailed(e)
    }
  }
  
  // Send `StartListening` to self when connection fails to try to reestablish the connection.
  onTransition {
    case _ -> NotConnected => (nextStateData: @unchecked) match { // error if ConnectionFailed is not set
      case ConnectionFailed(e) => {
        currentJobCompletion.map { f =>
          f.failure(e)
          currentJobCompletion = None
        }
        self ! StartListening
      }
    }
  }


  initialize

  // The callback that will be executed when the MessageService component receives a message on the queue
  // The MessageService thread blocks until the returned future completes. 
  // No new messages are requested until the future completes.
  private def deliverMessage(message: String): Future[Unit] = {
    Logger.info(s"Received message $message")
    currentJobCompletion = Some(Promise[Unit])
    val messageData = convertMessage(message)
    val f = currentJobCompletion.getOrElse(throw new Exception("Future had unexpectedly disappeared"))
    self ! messageData
    f.future
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }

}

