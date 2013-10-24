package org.overviewproject.jobhandler

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import akka.actor._
import akka.testkit._
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.jobhandler.MessageQueueActorProtocol._
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import javax.jms.Connection
import org.overviewproject.messagequeue.MessageQueueConnectionProtocol._
import org.overviewproject.messagequeue.ConnectionMonitorProtocol._
import org.overviewproject.jobhandler.MessageQueueActorProtocol2._
import org.specs2.mutable.Before

class MessageQueueActorSpec extends Specification with Mockito {

  //  class TestMessageQueueActor(messageHandler: ActorRef, messageService: MessageService) extends MessageQueueActor[String](messageService) {
  //    override def createMessageHandler: Props = Props(new ForwardingActor(messageHandler))
  //    override def convertMessage(message: String): String = s"CONVERTED$message"
  //
  //  }

  class TestMessageService extends MessageService2 {
    var deliverMessage: Message => Unit = _
    var lastAcknowledged: Option[Message] = None

    override def listenToConnection(connection: Connection, messageDelivery: Message => Unit): Unit = {
      deliverMessage = messageDelivery
    }
    
    override def acknowledge(message: Message): Unit = lastAcknowledged = Some(message)
  }
  
  class TestMessageQueueActor(messageHandler: ActorRef, messageService: MessageService2) extends MessageQueueActor2[String](messageService) {
    override def createMessageHandler: Props = Props(new ForwardingActor(messageHandler))
    override def convertMessage(message: String): String = s"CONVERTED$message"
  }

  "MessageQueueActor" should {

    trait MessageServiceProvider {
      val connection = smartMock[Connection]
      val messageService: MessageService2
    }

    abstract class MessageQueueActorSetup extends ActorSystemContext with Before {
      self: MessageServiceProvider =>
      var messageHandler: TestProbe = _
      var messageQueueActor: TestActorRef[TestMessageQueueActor] = _
      
      def before = {
        messageHandler = TestProbe()
        messageQueueActor = TestActorRef(new TestMessageQueueActor(messageHandler.ref, messageService))
      }
    }

    trait MockedMessageService extends MessageServiceProvider {
      override val messageService = smartMock[MessageService2]
    }

    trait FakeMessageService extends MessageServiceProvider {
      val testMessageService = new TestMessageService
      val messageService = testMessageService
      
      val messageText = "a message"
      val message = smartMock[Message]
      message.text returns messageText

    }

    "register itself with connection monitor" in new MessageQueueActorSetup with MockedMessageService {
      val connectionMonitor = TestProbe()

      messageQueueActor ! RegisterWith(connectionMonitor.ref)

      connectionMonitor.expectMsg(RegisterClient)
    }

    "start listening to incoming connection" in new MessageQueueActorSetup with MockedMessageService {
      messageQueueActor ! ConnectedTo(connection)

      there was one(messageService).listenToConnection(any, any)

    }

    "send incoming message to listener" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)

      testMessageService.deliverMessage(message)
      messageHandler.expectMsg(s"CONVERTED$messageText")
    }

    "acknowledge message when handled by listener" in new MessageQueueActorSetup with FakeMessageService {
      
      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)
      messageQueueActor ! MessageHandled
      
      messageService.lastAcknowledged must beSome(message)
    }

    "ignore message handled from listener when connection has failed" in new MessageQueueActorSetup with FakeMessageService {
      messageQueueActor ! ConnectedTo(connection)
      testMessageService.deliverMessage(message)
      
      messageQueueActor 
    }

    "ignore message handled from listener when new connection has been re-established" in {
      skipped
    }

    "hold new message after connection has been re-established, until listener has handled previous message" in {
      skipped
    }
  }
}