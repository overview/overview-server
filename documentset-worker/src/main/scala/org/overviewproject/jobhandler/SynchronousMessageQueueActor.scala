package org.overviewproject.jobhandler


import scala.language.postfixOps
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._

import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.util.Logger



import SynchronousMessageQueueActorFSM._

object SynchronousMessageQueueActorFSM {
  sealed trait State
  case object NotConnected extends State
  case object Ready extends State
  
  sealed trait Data
  case object Idle extends Data
  case class ConnectionFailed(e: Throwable) extends Data
  case class Listener(recipient: ActorRef) extends Data
} 


class SynchronousMessageQueueActor(messageRecipient: ActorRef) extends Actor with FSM[State, Data] {
  this: MessageServiceComponent =>

  // Time between reconnection attempts
  private val ReconnectionInterval = 1 seconds
  
  import MessageQueueActorProtocol._

  startWith(NotConnected, Idle)

  when(NotConnected) {
    case Event(StartListening, _) => {
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => {
          goto(Ready) using Listener(messageRecipient)
        }
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", StartListening, ReconnectionInterval, repeat = false)
          stay using ConnectionFailed(e)
        }

      }
    }
    case Event(ConnectionFailure, _) => stay 
  }

  when(Ready) {
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
    case Event(message, listener: Listener) => {
      listener.recipient ! message
      
      stay
    }
  }
  
  // Send `StartListening` to self when connection fails to try to reestablish the connection.
  onTransition {
    case _ -> NotConnected => (nextStateData: @unchecked) match { // error if ConnectionFailed is not set
      case ConnectionFailed(e) => self ! StartListening
    }
  }


  initialize

  // The callback that will be executed when the MessageService component receives a message on the queue
  // The MessageService thread blocks until the returned future completes. 
  // No new messages are requested until the future completes.
  private def deliverMessage(message: String): Future[Unit] = {
    self ! message
    Future.successful() // succeed immediately read new messages
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e) 
  }

}

