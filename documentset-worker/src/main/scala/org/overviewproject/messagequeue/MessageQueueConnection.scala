package org.overviewproject.messagequeue

import javax.jms.Connection
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import akka.actor._
import org.overviewproject.util.Configuration
import MessageQueueConnectionFSM._
import javax.jms.ExceptionListener
import javax.jms.JMSException
import org.overviewproject.util.Logger


trait Message {
  val content: String
  def acknowledge: Unit
}

trait ConnectionFactory {
  def createConnection(brokerUri: String, username: String, password: String): Try[Connection]
}

object ConnectionMonitorProtocol {
  case object RegisterClient
  case class ConnectedTo(connection: Connection)
  case object ConnectionFailed
}

object MessageQueueConnectionProtocol {
  case object StartConnection
  case class ConnectionFailure(e: Throwable)
}

object MessageQueueConnectionFSM {
  sealed trait State
  case object NotConnected extends State
  case object Connected extends State

  sealed trait Data
  case class NoConnection(clients: Seq[ActorRef]) extends Data
  case class QueueConnection(connection: Connection, clients: Seq[ActorRef]) extends Data
}

trait MessageQueueConnection extends Actor with FSM[State, Data] with ConnectionFactory {
  import MessageQueueConnectionProtocol._
  import ConnectionMonitorProtocol._
  
  private val BrokerUri: String = Configuration.messageQueue.getString("broker_uri")
  private val Username: String = Configuration.messageQueue.getString("username")
  private val Password: String = Configuration.messageQueue.getString("password")

  private val ReconnectionInterval = 1 seconds

  startWith(NotConnected, NoConnection(Seq.empty))

  when(NotConnected) {
    case Event(StartConnection, NoConnection(clients)) => startConnection(clients)
    case Event(RegisterClient, NoConnection(clients)) => stay using NoConnection(sender +: clients)
  }

  when(Connected) {
    case Event(ConnectionFailure(e), QueueConnection(connection, clients)) => restartConnection(e, clients)
    case Event(RegisterClient, QueueConnection(connection, clients)) => {
      sender ! ConnectedTo(connection)
      stay using QueueConnection(connection, sender +: clients)
    }
  }

  initialize

  private class FailureHandler extends ExceptionListener {
    def onException(e: JMSException): Unit = self ! ConnectionFailure
  }

  private def startConnection(clients: Seq[ActorRef]) = {
    val connectionAttempt = createConnection(BrokerUri, Username, Password)
    connectionAttempt match {
      case Success(connection) => {
        connection.setExceptionListener(new FailureHandler)
        clients.foreach { _ ! ConnectedTo(connection) }
        goto(Connected) using QueueConnection(connection, clients)
      }
      case Failure(e) => {
        Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
        setTimer("retry", StartConnection, ReconnectionInterval, repeat = false)
        stay
      }
    }
  }

  private def restartConnection(e: Throwable, clients: Seq[ActorRef]) = {
    Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
    clients.foreach { _ ! ConnectionFailed }
    
    self ! StartConnection
    goto(NotConnected) using NoConnection(clients)
  }
}


