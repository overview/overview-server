package org.overviewproject.jobhandler

import javax.jms._
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.util.{ Configuration, Logger }
import scala.concurrent.{ Await, Future }

class ApolloMessageService2(queueName: String) extends MessageService2 {

  private var session: Option[Session] = None
  private var consumer: Option[MessageConsumer] = None
  
  override def listenToConnection(connection: Connection, messageDelivery: MessageContainer => Unit): Unit = {
    val messageHandler = new MessageHandler(messageDelivery)
    consumer = Some(createConsumer(connection))
    consumer.map(_.setMessageListener(messageHandler))
    
    Logger.info(s"Listening to message broker: $queueName")    
  }

  override def acknowledge(message: MessageContainer): Unit = {
    // FIXME: Ugly downcast. Acknowledge should just be public in MessageContainer
    message.asInstanceOf[ApolloMessageContainer].message.acknowledge
  }

  override def stopListening: Unit = {
    consumer.map(_.close)
    consumer = None
    session.map(_.close)
    session = None
  }

  private case class ApolloMessageContainer(message: Message) extends MessageContainer {
    override val text = message.asInstanceOf[TextMessage].getText
    
    def acknowledge: Unit = message.acknowledge
  }
  
  private class MessageHandler(messageDelivery: MessageContainer => Unit) extends MessageListener {
    override def onMessage(message: Message): Unit =   messageDelivery(ApolloMessageContainer(message))
  }

  private def createConsumer(connection: Connection): MessageConsumer = {
    val destination = new StompJmsDestination(queueName)
    val newSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
    session = Some(newSession)
    
    newSession.createConsumer(destination)
  }

}

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