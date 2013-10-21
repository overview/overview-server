package org.overviewproject.jobhandler

import javax.jms.Connection
import org.overviewproject.util.Configuration
import org.fusesource.stomp.jms.StompJmsConnectionFactory

object MessageQueueConnection {
  
  lazy val connection: Connection = createConnection()
  
  private val BrokerUri: String = Configuration.messageQueue.getString("broker_uri")
  private val Username: String = Configuration.messageQueue.getString("username")
  private val Password: String = Configuration.messageQueue.getString("password")
  
  
  private def createConnection(): Connection = {
    val factory = new StompJmsConnectionFactory()
    factory.setBrokerURI(BrokerUri)
    val connection = factory.createConnection(Username, Password)
    
    connection.start
    
    connection
  }
}