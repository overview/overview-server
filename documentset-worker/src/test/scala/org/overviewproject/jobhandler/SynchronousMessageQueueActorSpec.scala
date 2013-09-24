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
  
  class TestSynchronousMessageQueueActor(recipient: ActorRef, failedConnectionAttempts: Int = 0) extends SynchronousMessageQueueActor(recipient)
      with MessageServiceComponent {

    var messageCallback: Option[String => Future[Unit]] = None
    var failureCallback: Option[Exception => Unit] = None
    var connectionCreationCount: Int = 0
   
    override val messageService = new MessageService {
      override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = {
        connectionCreationCount += 1
        messageCallback = Some(messageDelivery)
        failureCallback = Some(failureHandler)
        
        if (connectionCreationCount > failedConnectionAttempts) Success()
        else Failure(connectionFailure)
      }
    }
  }

  "SynchronousMessageActor" should {

    "send incoming messages to recipient and acknowledge immediately" in new ActorSystemContext {
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref))
      
      synchronousMessageQueueActor ! StartListening
      val completion = synchronousMessageQueueActor.underlyingActor.messageCallback.map(_(message))
      
      recipient.expectMsg(message)
      
      completion must beSome.which(_.isCompleted)
    }
    
    "restart connection if connection creation fails" in new ActorSystemContext {
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor = 
        TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref, failedConnectionAttempts = 1))
      
      synchronousMessageQueueActor ! StartListening
      
      awaitCond(synchronousMessageQueueActor.underlyingActor.connectionCreationCount == 2)
    }
    
    "restart connection if connection drops" in new ActorSystemContext {
      val recipient = TestProbe()
      val message = "A message"

      val synchronousMessageQueueActor = TestActorRef(new TestSynchronousMessageQueueActor(recipient.ref))

      synchronousMessageQueueActor ! StartListening
      synchronousMessageQueueActor ! ConnectionFailure(connectionFailure)
      
      synchronousMessageQueueActor.underlyingActor.connectionCreationCount must be equalTo(2)
      
    }
  }
}