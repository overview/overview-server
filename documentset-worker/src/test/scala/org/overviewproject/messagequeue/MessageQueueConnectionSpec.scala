package org.overviewproject.messagequeue

import scala.util.{ Success, Try }
import akka.testkit._
import org.overviewproject.messagequeue.MessageQueueConnectionProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import javax.jms.Connection
import org.specs2.mutable.Before
import org.overviewproject.util.MessageQueueConfig

class MessageQueueConnectionSpec extends Specification with Mockito {

  class TestMessageQueueConnection extends MessageQueueConnection {
    val connectionFactory = mock[ConnectionFactory]
    val connection = mock[Connection]
    connectionFactory.createConnection(any, any, any) returns Success(connection)

    override def createConnection(brokerUri: String, username: String, password: String): Try[Connection] =
      connectionFactory.createConnection(brokerUri, username, password)

  }

  "ConnectionMonitor" should {

    abstract class ConnectionSetup extends ActorSystemContext with Before {
      var messageQueueConnection: TestActorRef[TestMessageQueueConnection] = _
      lazy val connectionFactory = messageQueueConnection.underlyingActor.connectionFactory
      lazy val connection: Connection  = messageQueueConnection.underlyingActor.connection
      
      def before = {
        messageQueueConnection = TestActorRef(new TestMessageQueueConnection)
      }

    }

    "start the connection" in new ConnectionSetup {
      messageQueueConnection ! StartConnection

      there was one(connectionFactory).createConnection(any, any, any)
    }

    "restart connection on failure" in new ConnectionSetup {
      messageQueueConnection ! StartConnection
      messageQueueConnection ! ConnectionFailure(new Exception("failure"))

      there were two(connectionFactory).createConnection(any, any, any)
    }

    "send connection to clients registering before connection is started" in new ConnectionSetup {
      val client = TestProbe()

      messageQueueConnection.tell(RegisterClient, client.ref)
      messageQueueConnection ! StartConnection
      
      client.expectMsg(ConnectedTo(connection))
    }
    
    "send connection to clients registering  after connection has started" in new ConnectionSetup {
      val client = TestProbe()
      
      messageQueueConnection ! StartConnection
      messageQueueConnection.tell(RegisterClient, client.ref)
      
      client.expectMsg(ConnectedTo(connection))
    }
    
    "notify clients when connection fails" in {
      skipped
    }

    "notify clients when connection restarts" in {
      skipped
    }

  }
}