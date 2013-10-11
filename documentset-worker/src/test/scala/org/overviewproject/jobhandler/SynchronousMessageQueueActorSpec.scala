package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import akka.actor._
import akka.testkit._
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.jobhandler.MessageQueueActorProtocol._

class SynchronousMessageQueueActorSpec extends Specification {

  val connectionFailure = new Exception("connection failed")

  class TestMessageService(failedConnectionAttempts: Int = 0) extends MessageService {
    var messageCallback: Option[String => Future[Unit]] = None
    var failureCallback: Option[Exception => Unit] = None
    var connectionCreationCount: Int = 0

    override val queueName = "/queue/message-queue-name"

    override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
      connectionCreationCount += 1
      messageCallback = Some(messageDelivery)
      failureCallback = Some(failureHandler)

      if (connectionCreationCount > failedConnectionAttempts) Success()
      else Failure(connectionFailure)
    }
  }

  class TestSynchronousMessageQueueActor(recipient: ActorRef, messageService: MessageService,
                                         converter: String => String = identity)
      extends SynchronousMessageQueueActor[String](recipient, messageService, converter)

  "SynchronousMessageActor" should {

    "send incoming messages to recipient and acknowledge immediately" in new ActorSystemContext {
      val messageService = new TestMessageService
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))

      synchronousMessageQueueActor ! StartListening
      val completion = messageService.messageCallback.map(_(message))

      recipient.expectMsg(message)

      completion must beSome.which(_.isCompleted)
    }

    "restart connection if connection creation fails" in new ActorSystemContext {
      val messageService = new TestMessageService(failedConnectionAttempts = 1)
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor =
        TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))

      synchronousMessageQueueActor ! StartListening

      awaitCond(messageService.connectionCreationCount == 2)
    }

    "restart connection if connection drops" in new ActorSystemContext {
      val messageService = new TestMessageService
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService))

      synchronousMessageQueueActor ! StartListening
      synchronousMessageQueueActor ! ConnectionFailure(connectionFailure)

      messageService.connectionCreationCount must be equalTo (2)
    }

    "consume messages that fail to convert" in new ActorSystemContext {
      val messageService = new TestMessageService
      val recipient = TestProbe()
      val message = "a message"

      def failingConversion(m: String): String = throw new Exception("message unparsable for some reason")
      val synchronousMessageQueueActor =
        TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, messageService, failingConversion))

      synchronousMessageQueueActor ! StartListening
      val completion = messageService.messageCallback.map(_(message)) 

      completion must beSome.which(_.isCompleted)
      
      recipient.expectNoMsg
      
    } 
  }
}