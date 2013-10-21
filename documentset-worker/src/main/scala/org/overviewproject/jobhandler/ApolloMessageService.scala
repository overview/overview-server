package org.overviewproject.jobhandler

import javax.jms._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.util.{ Configuration, Logger }
import scala.concurrent.{ Await, Future }

class ApolloMessageService(override val queueName: String) extends MessageService {
  private val ConnectionRetryPause = 2000
  private val MaxConnectionAttempts = 5

  private val BrokerUri: String = Configuration.messageQueue.getString("broker_uri")
  private val Username: String = Configuration.messageQueue.getString("username")
  private val Password: String = Configuration.messageQueue.getString("password")
  private val QueueName: String = queueName

  private var connection: Connection = _
  private var consumer: MessageConsumer = _

  override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = Try {
    connection = MessageQueueConnection.connection

    val messageHandler = new MessageHandler(messageDelivery)
    consumer = createConsumer
    consumer.setMessageListener(messageHandler)

    Logger.info(s"Connected to message broker: $queueName")
  }

  private class MessageHandler(messageDelivery: String => Future[Unit]) extends MessageListener {

    // We need to wait for the job to complete, before returning
    // from call
    override def onMessage(message: Message): Unit = {
      val jobComplete = messageDelivery(message.asInstanceOf[TextMessage].getText)
      Await.result(jobComplete, Duration.Inf)
      jobComplete.value.map {
        case Success(_) => message.acknowledge
        case _ => // don't acknowledge. Connection is broken, so this probably doesn't matter
      }
    }
  }

  private class FailureHandler(handleFailure: Exception => Unit) extends ExceptionListener {
    override def onException(e: JMSException): Unit = handleFailure(e)
  }

  private def createConsumer: MessageConsumer = {
    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
    val destination = new StompJmsDestination(QueueName)

    session.createConsumer(destination)
  }
}