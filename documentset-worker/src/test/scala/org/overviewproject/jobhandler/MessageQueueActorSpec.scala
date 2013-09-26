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

class MessageQueueActorSpec extends Specification {

  val connectionFailure = new Exception("connection failed")

  class TestMessageService(failedConnectionAttempts: Int = 0) extends MessageService {
    var messageCallback: Option[String => Future[Unit]] = None
    var failureCallback: Option[Exception => Unit] = None
    var connectionCreationCount: Int = 0

    override val queueName = "Pepe leQueue"
      
    override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
      connectionCreationCount += 1
      messageCallback = Some(messageDelivery)
      failureCallback = Some(failureHandler)

      if (connectionCreationCount > failedConnectionAttempts) Success()
      else Failure(connectionFailure)
    }
  }

  class TestMessageQueueActor(messageHandler: ActorRef, messageService: MessageService) extends MessageQueueActor[String](messageService) {
    override def createMessageHandler: Props = Props(new ForwardingActor(messageHandler))
    override def convertMessage(message: String): String = s"CONVERTED$message"

  }
  
  "MessageQueueActor" should {
    
    "send incoming messages to handler" in new ActorSystemContext {
      val messageService = new TestMessageService
      val messageHandler = TestProbe()
      val message = "Some command as json"
        
      val messageQueueActor = TestActorRef(new TestMessageQueueActor(messageHandler.ref, messageService))
      
      messageQueueActor ! StartListening
      val completion = messageService.messageCallback.map(_(message))
      
      messageHandler.expectMsg(s"CONVERTED$message")
      
      messageQueueActor ! MessageHandled
      
      completion must beSome.which(c => c.isCompleted)
    }
    
    "restart connection if connection fails before message is received" in new ActorSystemContext {
      val messageService = new TestMessageService
      val messageHandler = TestProbe()
      val message = "Some command as json"
        
      val messageQueueActor = TestActorRef(new TestMessageQueueActor(messageHandler.ref, messageService))
      
      messageQueueActor ! StartListening

      messageService.failureCallback.map(_(new Exception("connection failed")))
      
      messageService.connectionCreationCount must be equalTo(2)
    }
    
  }
}