package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search
import org.overviewproject.jobhandler.DocumentSearcherProtocol.DocumentSearcherDone
import akka.actor._
import akka.testkit.TestActor
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.DocumentSearcherProtocol.StartSearch
import org.specs2.specification.Scope
import org.overviewproject.jobhandler.JobHandlerProtocol.JobDone

class SearchHandlerSpec extends Specification with Mockito {

  "SearchHandler" should {

    class TestSearchHandler(searchExists: Boolean, documentSearcherProbe: ActorRef) extends SearchHandler with SearchHandlerComponents {
      val storage = mock[Storage]
      storage.searchExists(anyLong, anyString) returns searchExists
      storage.createSearchResult(anyLong, anyString) returns 1l

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

    class SearchHandlerParent(searchExists: Boolean, parentProbe: ActorRef, documentSearcherProbe: ActorRef) extends Actor {
      val searchHandler = context.actorOf(Props(new TestSearchHandler(searchExists, documentSearcherProbe)))

      def receive = {
        case msg if sender == searchHandler => parentProbe forward msg
        case msg => searchHandler forward msg
      }

    }

    trait SearchHandlerSetup extends Scope {
      val documentSetId = 5l
      val query = "projectid:123 search terms"
      
      def createSearchHandlerParent(searchExists: Boolean, parentProbe: ActorRef, documentSearcherProbe: ActorRef)
        (implicit system: ActorSystem): ActorRef = 
          system.actorOf(Props(new SearchHandlerParent(searchExists, parentProbe, documentSearcherProbe)))
        
    }

    "send JobDone to parent if SearchResult already exists" in new ActorSystemContext with SearchHandlerSetup {
      val documentSearcherProbe = TestProbe()
      val parent = createSearchHandlerParent(searchExists = true, testActor, documentSearcherProbe.ref)
      
      parent ! Search(documentSetId, query, testActor)

      expectMsg(JobDone)
    }

    "create a new SearchResult and start DocumentSearcher if SearchResult doesn't exist" in new ActorSystemContext with SearchHandlerSetup {
      val documentSearcherProbe = TestProbe()

      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)

      parent ! Search(documentSetId, query, testActor)

      documentSearcherProbe.expectMsg(StartSearch(1l))
    }
    
    "send JobDone to parent when receiving Done from DocumentSearcher" in new ActorSystemContext with SearchHandlerSetup {
      val documentSearcherProbe = TestProbe()
      
      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)
      
      parent ! DocumentSearcherDone
      
      expectMsg(JobDone)
    }
    

  }

}