package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import akka.actor.ActorRef
import akka.actor.Actor
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.JobHandlerProtocol._
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search
import javax.jms.TextMessage


class JobHandlerSpec extends Specification with Mockito {

  "JobHandler" should {
    
    class TestSearchHandler(searchProbe: ActorRef, requestQueue: ActorRef) extends JobHandler(requestQueue) with MessageServiceComponent with SearchComponent {
      override val messageService = mock[MessageService]
      
      val actorCreator = new ActorCreator {
        override def produceSearchHandler: Actor = new ForwardingActor(searchProbe)
      }
    }
    
    class ForwardingActor(target: ActorRef) extends Actor {
      def receive = {
        case msg => target forward msg
      }
    }

    "start listening for messages" in new ActorSystemContext {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor))
      
      jobHandler ! StartListening
      
      val messageService = jobHandler.underlyingActor.messageService
      
      there was one(messageService).startListening(any)
    }
    
    
    "start search handler on incoming search command" in new ActorSystemContext {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor))
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
      val message = mock[TextMessage]
      message.getText() returns commandMessage
      
      jobHandler ! StartListening
        
      jobHandler ! CommandMessage(message)
      
      searchHandler.expectMsg(Search(documentSetId, query, testActor))
    }
    
    "complete message when Done is received" in new ActorSystemContext {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor))
      
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
      val message = mock[TextMessage]
      message.getText() returns commandMessage
      
      jobHandler ! StartListening
        
      jobHandler ! CommandMessage(message)
      jobHandler ! JobDone
      
      val messageService = jobHandler.underlyingActor.messageService
      
      there was one(messageService).complete(message)
    }
  }
}