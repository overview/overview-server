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
import org.specs2.specification.Scope


class JobHandlerSpec extends Specification with Mockito {

  "JobHandler" should {
    
    class TestSearchHandler(searchProbe: ActorRef, requestQueue: ActorRef, messageText: String) extends JobHandler(requestQueue) with MessageServiceComponent with SearchComponent {
      override val messageService = mock[MessageService]
     
      val message = mock[TextMessage]
      message.getText returns messageText
      
      messageService.waitForMessage returns message
      
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
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))
      
      jobHandler ! StartListening
      
      val messageService = jobHandler.underlyingActor.messageService
      
      there was one(messageService).waitForMessage
    }
    
    
    "start search handler on incoming search command" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))
      
      jobHandler ! StartListening
        
      
      searchHandler.expectMsg(Search(documentSetId, query, testActor))
    }
    
    "complete message when Done is received" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))
      
      jobHandler ! StartListening
      jobHandler ! JobDone
      
      val messageService = jobHandler.underlyingActor.messageService
      val message = jobHandler.underlyingActor.message
      
      there was one(messageService).complete(message)
    }

      "start listening after Done is received" in new ActorSystemContext with MessageSetup {
      val searchHandler = TestProbe()
      
      val jobHandler = TestActorRef(new TestSearchHandler(searchHandler.ref, testActor, commandMessage))
      
      val messageService = jobHandler.underlyingActor.messageService
      
      jobHandler ! StartListening
      jobHandler ! JobDone
      
      there were two(messageService).waitForMessage

    }
}
}