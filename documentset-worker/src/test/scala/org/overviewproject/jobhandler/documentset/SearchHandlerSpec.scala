package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }

import org.overviewproject.jobhandler.documentset.DocumentSetJobHandlerProtocol.JobDone
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope


class SearchHandlerSpec extends Specification with Mockito {

  "SearchHandler" should {

    
    class TestSearchHandler(searchExists: Boolean, documentSearcherProbe: ActorRef) extends SearchHandler with SearchHandlerComponents {
      val storage = mock[Storage]

      storage.searchExists(anyLong, anyString) returns searchExists
      storage.createSearchResult(anyLong, anyString) returns 1l
      storage.queryForProject(anyLong, anyString) returns "search terms"
      
      val actorCreator = new ActorCreator { // can't mock creation of actors
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new ForwardingActor(documentSearcherProbe)
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
      val searchTerms = "search terms"
      
      def createSearchHandlerParent(searchExists: Boolean, parentProbe: ActorRef, documentSearcherProbe: ActorRef)
        (implicit system: ActorSystem): ActorRef = 
          system.actorOf(Props(new SearchHandlerParent(searchExists, parentProbe, documentSearcherProbe)))
        
    }

    abstract class SearchHandlerContext extends ActorSystemContext with SearchHandlerSetup
    
    "send JobDone to parent if SearchResult already exists" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()
      val parent = createSearchHandlerParent(searchExists = true, testActor, documentSearcherProbe.ref)
      
      parent ! SearchDocumentSet(documentSetId, searchTerms)

      expectMsg(JobDone)
    }

    "create a new SearchResult and start Searcher if SearchResult doesn't exist" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()

      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)

      parent ! SearchDocumentSet(documentSetId, searchTerms)

      documentSearcherProbe.expectMsg(StartSearch(1l, documentSetId, searchTerms))
    }
    
    "send JobDone to parent when receiving SearchComplete from Searcher" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()
      
      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)
      
      parent ! SearchDocumentSet(documentSetId, searchTerms)
      parent ! SearchComplete
      
      expectMsg(JobDone)
    }
    
    "set SearchResult state to Complete when receiving SearchComplete from Searcher" in new SearchHandlerContext {
      
      val searchHandler = TestActorRef(new TestSearchHandler(searchExists = false, testActor))
      val searchHandlerWatcher = TestProbe()
      
      searchHandlerWatcher watch searchHandler
      
      searchHandler ! SearchDocumentSet(documentSetId, searchTerms)
      searchHandler ! SearchComplete

      val storage = searchHandler.underlyingActor.storage
      
      there was one(storage).completeSearch(1l, documentSetId, searchTerms)
      searchHandlerWatcher.expectMsgType[Terminated]
    }
    
    "set SearchResultState to Error when receiving SearchFailure from Searcher" in new SearchHandlerContext {
      val searchHandler = TestActorRef(new TestSearchHandler(searchExists = false, testActor))
      val searchHandlerWatcher = TestProbe()
      
      searchHandlerWatcher watch searchHandler
      
      val error = new Exception("exception from RequestQueue")
      
      searchHandler ! SearchDocumentSet(documentSetId, searchTerms)
      searchHandler ! SearchFailure(error)
      
      val storage = searchHandler.underlyingActor.storage
      
      there was one(storage).failSearch(1l, documentSetId, searchTerms)
      searchHandlerWatcher.expectMsgType[Terminated]
    }
  }

}