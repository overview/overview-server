package org.overviewproject.messagequeue

import scala.util.{ Success, Try }

import akka.testkit._

import org.overviewproject.messagequeue.MessageQueueConnectionProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito

import org.specs2.mutable.Specification

import javax.jms.Connection

class MessageQueueConnectionSpec extends Specification with Mockito {

  class TestMessageQueueConnection extends MessageQueueConnection {
    val connectionFactory = mock[ConnectionFactory]
    connectionFactory.createConnection(any, any, any) returns(Success(mock[Connection]))
    
    override def createConnection(brokerUri: String, username: String, password: String): Try[Connection] =
      connectionFactory.createConnection(brokerUri, username, password)
    
  }
  
  "ConnectionMonitor" should {
    
    "start the connection" in new ActorSystemContext {
      val connection = TestActorRef(new TestMessageQueueConnection)
      
      connection ! StartConnection
      
      there was one(connection.underlyingActor.connectionFactory).createConnection(any, any, any)
    }
    
    "restart connection on failure" in new ActorSystemContext {
      val connection = TestActorRef(new TestMessageQueueConnection)
      
      connection ! StartConnection
      connection ! ConnectionFailure(new Exception("failure"))
      
      there were two(connection.underlyingActor.connectionFactory).createConnection(any, any, any)
      
    }
    
    "notify clients when connection fails" in {
      skipped
    }
    
    "notify clients when connection restarts" in {
      skipped
    }
    
  }
}