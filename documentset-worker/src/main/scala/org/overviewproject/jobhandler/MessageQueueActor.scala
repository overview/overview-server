package org.overviewproject.jobhandler

import scala.language.postfixOps
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._

import org.overviewproject.jobhandler.JobProtocol._
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

trait MessageQueueActor[T] extends Actor with FSM[State, Data] with MessageHandling[T] {
  this: MessageServiceComponent =>

  // Time between reconnection attempts
  private val ReconnectionInterval = 1 seconds
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
    case Event(JobDone(id), _) => stay
    case Event(ConnectionFailure, _) => stay // For some reason, and extra event is generated when job is in progress
  }

  when(Ready) {
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
    case Event(JobStart(id), _) => {
      context.parent ! JobStart(id)
      stay
    }
    case Event(message, listener: Listening) => {
      listener.messageHandler ! message
      goto(WaitingForCompletion) using listener
    }
  }

  when(WaitingForCompletion) {
    case Event(JobDone(id), _) => {
      context.parent ! JobDone(id)
      currentJobCompletion.map(_.success())
      currentJobCompletion = None
      goto(Ready)
    }
    case Event(ConnectionFailure(e), _) => {
      Logger.error(s"Connection Failure: ${e.getMessage}", e)
      goto(NotConnected) using ConnectionFailed(e)
    }
  }
  
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

  private def deliverMessage(message: String): Future[Unit] = {
    currentJobCompletion = Some(Promise[Unit])
    val messageData = convertMessage(message)
    self ! messageData
    currentJobCompletion.get.future
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }

}

