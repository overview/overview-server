package org.overviewproject.jobhandler.documentset

import org.specs2.mutable.Specification
import akka.actor._
import org.overviewproject.test.ForwardingActor
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol.SearchCommand
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol.DeleteCommand
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.JobProtocol._

class DocumentSetMessageHandlerSpec extends Specification {
  
  
  class TestMessageHandler(specificHandlerProbe: ActorRef) extends DocumentSetMessageHandler
      with SearchComponent {

    val actorCreator = new ActorCreator {
      override def produceSearchHandler: Actor = new ForwardingActor(specificHandlerProbe)
      override def produceDeleteHandler: Actor = new ForwardingActor(specificHandlerProbe)
    }
  }
  
  "DocumentSetMessageHandler" should {
    
    "start search handler" in new ActorSystemContext {
      val documentSetId = 1l
      val query = "query string"
      val searchCommand = SearchCommand(documentSetId, query)
      val searchMessage = SearchDocumentSet(documentSetId, query)

      val searchHandler = TestProbe()
      
      val messageHandler = TestActorRef(new TestMessageHandler(searchHandler.ref))
      
      messageHandler ! searchCommand
      
      searchHandler.expectMsg(searchMessage)
    }
    
    "start delete handler" in new ActorSystemContext {
      val documentSetId = 1l
      val deleteCommand = DeleteCommand(documentSetId)
      val deleteMessage = DeleteDocumentSet(documentSetId)
      
      val deleteHandler = TestProbe()
      
      val messageHandler = TestActorRef(new TestMessageHandler(deleteHandler.ref))
      
      messageHandler ! deleteCommand
      
      deleteHandler.expectMsg(deleteMessage)
    }
    
    "tell parent JobDone" in new ActorSystemContext {
      val parentProbe = TestProbe()
      val documentSetId = 1l
      val deleteCommand = DeleteCommand(documentSetId)
      val deleteMessage = DeleteDocumentSet(documentSetId)
      
      val deleteHandler = TestProbe()
      
      val messageHandler = TestActorRef(Props(new TestMessageHandler(deleteHandler.ref)), parentProbe.ref, "Message Handler")
      
      messageHandler ! deleteCommand
      messageHandler ! JobDone(documentSetId)
      
      parentProbe.expectMsg(JobDone(documentSetId))
    }
  }

}