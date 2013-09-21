package org.overviewproject.jobhandler

import javax.jms._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.Try
import scala.util.{Failure, Success, Try}
import akka.actor._

import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.util.{ Configuration, Logger }


/**
 * A component that listens for and responds to incoming messages.
 */
trait MessageServiceComponent {

  val messageService: MessageService

  trait MessageService {
    /**
     *  Create a connection to the message queue.
     *  @returns `Success` if connection is  established, `Failure` otherwise
     *  @param messageDelivery will be called when a new message is received. The method
     *  should return a `Future` that will be completed when the job specified in the message
     *  has finished processing.
     *  @param failureHandler will be called if the connection fails.
     */
    def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit]
  }
}


/**
 * Implementation of connecting to the message queue and receiving and responding to
 * messages.
 * Sets up an asynchronous callback for messages, which blocks on a `Future` completion before
 * acknowledging the message
 */
trait MessageServiceComponentImpl extends MessageServiceComponent {
  class MessageServiceImpl(queueName: String) extends MessageService {
    private val ConnectionRetryPause = 2000
    private val MaxConnectionAttempts = 5

    private val BrokerUri: String = Configuration.messageQueue.brokerUri
    private val Username: String = Configuration.messageQueue.username
    private val Password: String = Configuration.messageQueue.password
    private val QueueName: String = queueName

    private var connection: Connection = _
    private var consumer: MessageConsumer = _

    override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = Try {
      val factory = new StompJmsConnectionFactory()
      factory.setBrokerURI(BrokerUri)
      connection = factory.createConnection(Username, Password)
      connection.setExceptionListener(new FailureHandler(failureHandler))
      val messageHandler = new MessageHandler(messageDelivery)
      consumer = createConsumer
      consumer.setMessageListener(messageHandler)
      connection.start
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
}
