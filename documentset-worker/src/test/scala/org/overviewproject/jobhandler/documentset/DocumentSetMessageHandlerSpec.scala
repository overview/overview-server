package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit._

import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.messagequeue.MessageHandlerProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol._
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }

import org.specs2.mutable.Specification

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
    
    "tell parent MessageHandled when JobDone is received" in new ActorSystemContext {
      val parentProbe = TestProbe()
      val documentSetId = 1l
      val deleteCommand = DeleteCommand(documentSetId)
      val deleteMessage = DeleteDocumentSet(documentSetId)
      
      val deleteHandler = TestProbe()
      
      val messageHandler = TestActorRef(Props(new TestMessageHandler(deleteHandler.ref)), parentProbe.ref, "Message Handler")
      
      messageHandler ! deleteCommand
      messageHandler ! JobDone(documentSetId)
      
      parentProbe.expectMsg(MessageHandled)
    }
  }

}