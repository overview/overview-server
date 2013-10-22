package org.overviewproject.messagequeue

import javax.jms.Connection
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor.Actor
import akka.actor.FSM
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

object MessageQueueConnectionProtocol {
  case object StartConnection
  case class ConnectionFailure(e: Throwable)
  
}

object MessageQueueConnectionFSM {
  sealed trait State
  case object NotConnected extends State
  case object Connected extends State

  sealed trait Data
  case object NoConnection extends Data
  case class ConnectedTo(connection: Connection) extends Data
}

trait MessageQueueConnection extends Actor with FSM[State, Data] with ConnectionFactory {
  import MessageQueueConnectionProtocol._

  private val BrokerUri: String = Configuration.messageQueue.getString("broker_uri")
  private val Username: String = Configuration.messageQueue.getString("username")
  private val Password: String = Configuration.messageQueue.getString("password")
  
  private val ReconnectionInterval = 1 seconds
  
  startWith(NotConnected, NoConnection)

  when(NotConnected) {
    case Event(StartConnection, _) => {
      val connectionAttempt = createConnection(BrokerUri, Username, Password)
      connectionAttempt match {
        case Success(connection) => {
          connection.setExceptionListener(new FailureHandler)
          goto(Connected) using ConnectedTo(connection)
        }
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", StartConnection, ReconnectionInterval, repeat = false)
          stay
        }
      }
    }
  }
  
  when(Connected) {
    case Event(ConnectionFailure(e), ConnectedTo(connection)) => {
      Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
      self ! StartConnection
      goto(NotConnected) using NoConnection
    }
  }

  initialize
  
  private class FailureHandler extends ExceptionListener {
    def onException(e: JMSException): Unit = self ! ConnectionFailure
  }

}

