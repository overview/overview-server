package org.overviewproject.messagequeue

import akka.actor._
import akka.testkit._

import org.overviewproject.messagequeue.ConnectionMonitorProtocol._
import org.overviewproject.messagequeue.AcknowledgingMessageReceiverProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.{ Before, Specification }
import javax.jms.Connection

class MessageReceiverSpec extends Specification with Mockito {

  def testConversion(message: String): String = message match {
    case "unknown" => throw new Exception("could not convert message")
    case _ => s"CONVERTED$message"
  }
  
  class TestMessageService extends MessageService {
    var currentConnection: Connection = _
    var deliverMessage: MessageContainer => Unit = _

    override def listenToConnection(connection: Connection, messageDelivery: MessageContainer => Unit): Unit = {
      currentConnection = connection
      deliverMessage = messageDelivery
    }

    override def stopListening: Unit = {
      currentConnection = null
    }
    
    override def acknowledge(message: MessageContainer): Unit = {}
  }


  
  "MessageReceiver" should {

    trait MessageServiceProvider {
      val messageService: MessageService 
    }
    
    abstract class MessageReceiverSetup extends ActorSystemContext with Before {
      self: MessageServiceProvider =>
      var messageReceiver: ActorRef = _
      var messageRecipient: TestProbe = _
      var connectionMonitor: TestProbe = _
      var connection: Connection = _

      def before = {
        messageRecipient = TestProbe()
    	messageReceiver = TestActorRef(new MessageReceiver(messageRecipient.ref, messageService, testConversion))
    	connectionMonitor = TestProbe()
    	connection = smartMock[Connection]
      }
    }

    trait MockMessageServiceProvider extends MessageServiceProvider {
      override val messageService = smartMock[MessageService]
    }
   
    trait FakeMessageServiceProvider extends MessageServiceProvider {
      val messageText = "a message"
      val message = smartMock[MessageContainer]
      message.text returns messageText
      
      override val messageService = new TestMessageService
    }
    
    "register itself with connection monitor" in new MessageReceiverSetup with MockMessageServiceProvider {
    	
      messageReceiver ! RegisterWith(connectionMonitor.ref)
      
      connectionMonitor.expectMsg(RegisterClient)
    }

    "start listening to incoming connection" in new MessageReceiverSetup with MockMessageServiceProvider {
      messageReceiver ! ConnectedTo(connection)
      
      there was one(messageService).listenToConnection(any, any)
    }

    "send incoming message to listener" in new MessageReceiverSetup with FakeMessageServiceProvider {
      messageReceiver ! ConnectedTo(connection)
      messageService.deliverMessage(message)
      
      messageRecipient.expectMsg(testConversion(messageText))
    }

    "close message service when connection fails" in new MessageReceiverSetup with MockMessageServiceProvider {
      messageReceiver ! ConnectedTo(connection)
      messageReceiver ! ConnectionFailed
      
      there was one(messageService).stopListening
    }
    
    "ignore messages that can't be converted" in new MessageReceiverSetup with FakeMessageServiceProvider {
      val unknownMessage = smartMock[MessageContainer]
      unknownMessage.text returns "unknown"

      messageReceiver ! ConnectedTo(connection)
      messageService.deliverMessage(unknownMessage)
      
      messageRecipient.expectNoMsg
    }
  }
  //  val connectionFailure = new Exception("connection failed")
  //
  //  class TestMessageService(failedConnectionAttempts: Int = 0) extends MessageService {
  //    var messageCallback: Option[String => Future[Unit]] = None
  //    var failureCallback: Option[Exception => Unit] = None
  //    var connectionCreationCount: Int = 0
  //
  //    override val queueName = "/queue/message-queue-name"
  //
  //    override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
  //      connectionCreationCount += 1
  //      messageCallback = Some(messageDelivery)
  //      failureCallback = Some(failureHandler)
  //
  //      if (connectionCreationCount > failedConnectionAttempts) Success()
  //      else Failure(connectionFailure)
  //    }
  //  }
  //
  //  class TestSynchronousMessageQueueActor(recipient: ActorRef, messageService: MessageService,
  //                                         converter: String => String = identity)
  //      extends SynchronousMessageQueueActor[String](recipient, messageService, converter)
  //
  //  "SynchronousMessageActor" should {
  //
  //    "send incoming messages to recipient and acknowledge immediately" in new ActorSystemContext {
  //      val messageService = new TestMessageService
  //      val recipient = TestProbe()
  //      val message = "A message"
  //
  //      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))
  //
  //      synchronousMessageQueueActor ! StartListening
  //      val completion = messageService.messageCallback.map(_(message))
  //
  //      recipient.expectMsg(message)
  //
  //      completion must beSome.which(_.isCompleted)
  //    }
  //
  //    "restart connection if connection creation fails" in new ActorSystemContext {
  //      val messageService = new TestMessageService(failedConnectionAttempts = 1)
  //      val recipient = TestProbe()
  //      val message = "A message"
  //
  //      val synchronousMessageQueueActor =
  //        TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))
  //
  //      synchronousMessageQueueActor ! StartListening
  //
  //      awaitCond(messageService.connectionCreationCount == 2)
  //    }
  //
  //    "restart connection if connection drops" in new ActorSystemContext {
  //      val messageService = new TestMessageService
  //      val recipient = TestProbe()
  //      val message = "A message"
  //
  //      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))
  //
  //      synchronousMessageQueueActor ! StartListening
  //      synchronousMessageQueueActor ! ConnectionFailure(connectionFailure)
  //
  //      messageService.connectionCreationCount must be equalTo (2)
  //    }
  //
  //    "consume messages that fail to convert" in new ActorSystemContext {
  //      val messageService = new TestMessageService
  //      val recipient = TestProbe()
  //      val message = "a message"
  //
  //      def failingConversion(m: String): String = throw new Exception("message unparsable for some reason")
  //      val synchronousMessageQueueActor =
  //        TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService, failingConversion))
  //
  //      synchronousMessageQueueActor ! StartListening
  //      val completion = messageService.messageCallback.map(_(message)) 
  //
  //      completion must beSome.which(_.isCompleted)
  //      
  //      recipient.expectNoMsg
  //      
  //    } 
  //  }
}