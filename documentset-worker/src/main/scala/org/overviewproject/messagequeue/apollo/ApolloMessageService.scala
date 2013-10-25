package org.overviewproject.messagequeue.apollo

import javax.jms._
import scala.concurrent.duration._
import org.fusesource.stomp.jms.StompJmsDestination
import org.overviewproject.util.Logger
import org.overviewproject.messagequeue.{ MessageService, MessageContainer }


class ApolloMessageService(queueName: String, acknowledgeMode: Int = Session.AUTO_ACKNOWLEDGE) extends MessageService {

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
    val newSession = connection.createSession(false, acknowledgeMode)
    session = Some(newSession)
    
    newSession.createConsumer(destination)
  }

}

