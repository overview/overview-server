package org.overviewproject.jobhandler


import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search
import org.overviewproject.jobhandler.JobHandlerProtocol.Done
import akka.actor._
import akka.testkit.TestActor
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.DocumentSearcherProtocol.StartSearch


class SearchHandlerSpec extends Specification with Mockito {
  
  "SearchHandler" should {
    
      class TestSearchHandler(searchExists: Boolean, documentSearcherProbe: ActorRef) extends SearchHandler with SearchHandlerComponents {
        val storage = mock[Storage]
        storage.searchExists(anyLong, anyString) returns searchExists
        
        val actorCreator = new ActorCreator { // can't mock creation of actors
          override def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor = 
            new ForwardingActor(documentSearcherProbe)  
        }
        
      }
      
      class ForwardingActor(target: ActorRef) extends Actor {
        def receive = {
          case msg => target forward msg
        }
      }
    
    "send Done to parent if SearchResult already exists" in new ActorSystemContext {
      val documentSetId = 5l
      val query = "projectid:123 search terms"
      val documentSearcherProbe = TestProbe()
      
      val parent = system.actorOf(Props(new Actor {
        val searchHandler = context.actorOf(Props(new TestSearchHandler(searchExists = true, documentSearcherProbe.ref)))
        
        def receive = {
          case msg if sender == searchHandler => testActor forward msg
          case msg => searchHandler forward msg
        }
      }))
      
      parent ! Search(documentSetId, query, testActor)
      
      expectMsg(Done)
    }
    
    "send StartSearch to a new DocumentSearcher" in new ActorSystemContext {
      val documentSetId = 5l
      val query = "projectid:123 search terms"
      val documentSearcherProbe = TestProbe()
      
      val parent = system.actorOf(Props(new Actor {
        val searchHandler = context.actorOf(Props(new TestSearchHandler(searchExists = false, documentSearcherProbe.ref)))
        
        def receive = {
          case msg if sender == searchHandler => testActor forward msg
          case msg => searchHandler forward msg
        }
      }))
      
      parent ! Search(documentSetId, query, testActor)
      
      documentSearcherProbe.expectMsg(StartSearch())
      
    }
    
  }

}