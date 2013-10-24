package org.overviewproject.messagequeue.apollo

import javax.jms.Connection
import scala.util.Try
import akka.actor.Props
import org.fusesource.stomp.jms.StompJmsConnectionFactory
import org.overviewproject.messagequeue.MessageQueueConnection
import org.overviewproject.util.Configuration



class ApolloMessageQueueConnection extends MessageQueueConnection {

  private val BrokerUri: String = Configuration.messageQueue.getString("broker_uri")
  private val Username: String = Configuration.messageQueue.getString("username")
  private val Password: String = Configuration.messageQueue.getString("password")

  override def createConnection(brokerUri: String, username: String, password: String): Try[Connection] = Try {
    val factory = new StompJmsConnectionFactory()
    factory.setBrokerURI(BrokerUri)
    val connection = factory.createConnection(Username, Password)
    
    connection.start
    
    connection
  }
}


object ApolloMessageQueueConnection {
  def apply(): Props = Props[ApolloMessageQueueConnection]
}