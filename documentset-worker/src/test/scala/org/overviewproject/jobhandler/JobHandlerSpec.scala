package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import akka.actor.ActorRef
import akka.actor.Actor
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.JobHandlerProtocol._
import org.overviewproject.jobhandler.SearchHandlerProtocol.SearchDocumentSet
import javax.jms.TextMessage
import org.specs2.specification.Scope
import scala.util.Try
import scala.concurrent.Future
import scala.util.Success

class JobHandlerSpec extends Specification with Mockito {

  "JobHandler" should {

    class TestJobHandler(searchProbe: ActorRef, requestQueue: ActorRef, messageText: String) extends JobHandler(requestQueue) with MessageServiceComponent with SearchComponent {
      var messageCallback: Option[String => Future[Unit]] = None
      var failureCallback: Option[Exception => Unit] = None
      var connectionCreationCount: Int = 0
      
      override val messageService = new MessageService {
        override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = { 
          connectionCreationCount += 1
          messageCallback = Some(messageDelivery)
          failureCallback = Some(failureHandler)
          Success()
        }
      }

      val actorCreator = new ActorCreator {
        override def produceSearchHandler: Actor = new ForwardingActor(searchProbe)
      }
    }

    class ForwardingActor(target: ActorRef) extends Actor {
      def receive = {
        case msg => target forward msg
      }
    }

    trait MessageSetup extends Scope {
      val documentSetId = 5l
      val query = "projectid:333 search terms"

      val commandMessage = s"""
      {
        "cmd" : "search",
        "args" : {
          "documentSetId" : $documentSetId,
          "query" : "$query"
        }
      }"""
    }

    "start listening for messages" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestJobHandler(searchHandler.ref, testActor, commandMessage))
      
      
      jobHandler ! StartListening

      val testJobHandler = jobHandler.underlyingActor
      testJobHandler.messageCallback must beSome
      testJobHandler.failureCallback must beSome
      testJobHandler.connectionCreationCount must be equalTo(1)
    }

    "start search handler on incoming search command" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestJobHandler(searchHandler.ref, testActor, commandMessage))
      
       
      jobHandler ! StartListening
      val completion = jobHandler.underlyingActor.messageCallback.map(f => f(commandMessage))
      
      searchHandler.expectMsg(SearchDocumentSet(documentSetId, query, testActor))
      

      completion must beSome.which(f => !f.isCompleted)
    }

    "complete jobCompletion future when JobDone is received" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestJobHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening
      val completion = jobHandler.underlyingActor.messageCallback.map(f => f(commandMessage))
      jobHandler ! JobDone

       completion must beSome.which(f => f.isCompleted)
    }
    
    "restart connection if connection fails" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestJobHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening

      val testJobHandler = jobHandler.underlyingActor
      testJobHandler.failureCallback.map(f => f(new Exception("connection failed")))
      
      testJobHandler.connectionCreationCount must be equalTo(2)
    }
    
    "restart connection if connection fails before job is done" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()

      val jobHandler = TestActorRef(new TestJobHandler(searchHandler.ref, testActor, commandMessage))

      jobHandler ! StartListening

      val testJobHandler = jobHandler.underlyingActor
      val completion = testJobHandler.messageCallback.map(f => f(commandMessage))
      testJobHandler.failureCallback.map(f => f(new Exception("connection failed")))
          
      testJobHandler.connectionCreationCount must be equalTo(2)      
    }
  }
}