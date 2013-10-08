package plugins

import scala.language.postfixOps
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success, Try }

import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }

import javax.jms.{ DeliveryMode, ExceptionListener, JMSException, MessageProducer, Session }
import play.api.{ Application, Logger, Play }
import play.api.Play.current
import play.api.Plugin
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/** Message queue config values set in application.conf */
trait MessageQueueConfiguration {
  private val QueuePrefix = "message_queue"

  val BrokerUri = configValue("broker_uri")
  val Username = configValue("username")
  val Password = configValue("password")
  val QueueName = configValue("queue_name")

  private def configValue(key: String): String =
    Play.current.configuration.getString(s"$QueuePrefix.$key")
      .getOrElse(throw new Exception(s"Unable to read message_queue configuration for $key"))
}

/**
 * Plugin that manages a connection to the Message Broker.
 * Set `message_queue.mock=true` in application.conf to mock out
 * the connection.
 */
class StompPlugin(application: Application) extends Plugin with MessageQueueConfiguration {
  lazy val queueConnection: MessageQueueConnection =
    if (useMock) new MockMessageQueueConnection
    else new StompJmsMessageQueueConnection(QueueName)

  override def onStart(): Unit = queueConnection

  override def onStop(): Unit = queueConnection.close

  private def useMock: Boolean = Play.current.configuration.getBoolean("message_queue.mock").getOrElse(false)
}

/** Operations on the message queue */
trait MessageQueueConnection {
  /**
   * Send a message. @return a `Left[Unit]` if the connection
   * is down, `Right[Unit]` otherwise
   */
  def send(messageText: String): Either[Unit, Unit]

  /**
   * Send a message to the `messageGroup`. @return a `Left[Unit]` if the connection
   * is down, `Right[Unit]` otherwise
   */
  def send(messageText: String, messageGroup: String): Either[Unit, Unit]

  /**
   *  Close the connection. Should only be called when application
   *  is shutting down, since there is no way to reconnect.
   */
  def close: Unit
}

class StompJmsMessageQueueConnection(queueName: String) extends MessageQueueConnection with MessageQueueConfiguration {
  private val messageSender: MessageSender = new MessageSender

  override def send(messageText: String): Either[Unit, Unit] = messageSender.send(messageText)
  override def send(messageText: String, messageGroup: String): Either[Unit, Unit] = messageSender.send(messageText, Some(messageGroup))

  override def close: Unit = messageSender.close

  private class MessageSender extends ExceptionListener {
    private val connectionStatus = new ConnectionStatus

    private var session: Option[Session] = None
    private var producer: Option[MessageProducer] = None

    createSession

    override def onException(exception: JMSException): Unit = {
      Logger.error(s"Exception detected: ${exception.getMessage()}")

      if (!connectionStatus.connectionFailed_isRestartInProgress)
        createSession
    }

    def send(messageText: String, messageGroup: Option[String] = None): Either[Unit, Unit] =
      Either.cond(connectionStatus.isConnected,
        for {
          s <- session
          p <- producer
        } {
          val message = s.createTextMessage(messageText)
          messageGroup.map { g => message.setStringProperty("message_group", g) }

          p.send(message)
        }, ())

    def close: Unit = session.map(_.close)

    private def createSession: Unit = {
      val factory = new StompJmsConnectionFactory()
      factory.setBrokerURI(BrokerUri)

      val destination = new StompJmsDestination(queueName)

      attemptSessionCreation(factory, destination)
    }

    private def attemptSessionCreation(factory: StompJmsConnectionFactory, destination: StompJmsDestination): Unit =
      Try {
        Logger.debug(s"Attempting to create Session.")
        val connection = factory.createConnection(Username, Password)
        connection.setExceptionListener(this)
        connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
      } match {
        case Success(s) => {
          Logger.debug("Session created")
          session = Some(s)
          producer = session.map(_.createProducer(destination))
          producer.map(_.setDeliveryMode(DeliveryMode.NON_PERSISTENT))
          connectionStatus.connectionSucceeded
        }
        case Failure(e) => Akka.system.scheduler.scheduleOnce(1 second) {
          Logger.info(e.getMessage(), e) // Don't log to error to avoid generating error emails during startup
          attemptSessionCreation(factory, destination)
        }
      }

    private class ConnectionStatus {
      private var isConnectionUp: Boolean = false

      def isConnected: Boolean = this.synchronized { isConnectionUp }

      def connectionFailed_isRestartInProgress: Boolean = this.synchronized {
        val restartInProgress = !isConnectionUp
        isConnectionUp = false

        restartInProgress
      }

      def connectionSucceeded: Unit = this.synchronized { isConnectionUp = true }
    }

  }
}

class MockMessageQueueConnection extends MessageQueueConnection {
  override def send(messageText: String): Either[Unit, Unit] = Right()
  override def send(messageText: String, messageGroup: String): Either[Unit, Unit] = Right()

  override def close: Unit = {}
}
