package org.overviewproject.jobhandler

import org.overviewproject.jobhandler.DocumentSearcherProtocol.{DocumentSearcherDone, StartSearch}
import org.overviewproject.jobhandler.JobHandlerProtocol.JobDone
import org.overviewproject.jobhandler.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.actor._
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import org.overviewproject.http.RequestQueueProtocol.Failure
import org.overviewproject.jobs.models.Search


class SearchHandlerSpec extends Specification with Mockito {

  "SearchHandler" should {

    
    class TestSearchHandler(searchExists: Boolean, documentSearcherProbe: ActorRef) extends SearchHandler with SearchHandlerComponents {
      val storage = mock[Storage]
      storage.queryForProject(anyLong, anyString) returns Search(1l, "projectid: 1234 search terms")
      storage.searchExists(any) returns searchExists
      storage.createSearchResult(any) returns 1l
      
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
      val searchTerms = "search terms"
      val query = s"projectid: 1234 $searchTerms"
      
      def createSearchHandlerParent(searchExists: Boolean, parentProbe: ActorRef, documentSearcherProbe: ActorRef)
        (implicit system: ActorSystem): ActorRef = 
          system.actorOf(Props(new SearchHandlerParent(searchExists, parentProbe, documentSearcherProbe)))
        
    }

    abstract class SearchHandlerContext extends ActorSystemContext with SearchHandlerSetup
    
    "send JobDone to parent if SearchResult already exists" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()
      val parent = createSearchHandlerParent(searchExists = true, testActor, documentSearcherProbe.ref)
      
      parent ! SearchDocumentSet(documentSetId, searchTerms, testActor)

      expectMsg(JobDone)
    }

    "create a new SearchResult and start DocumentSearcher if SearchResult doesn't exist" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()

      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)

      parent ! SearchDocumentSet(documentSetId, searchTerms, testActor)

      documentSearcherProbe.expectMsg(StartSearch(1l))
    }
    
    "send JobDone to parent when receiving Done from DocumentSearcher" in new SearchHandlerContext {
      val documentSearcherProbe = TestProbe()
      
      val parent = createSearchHandlerParent(searchExists = false, testActor, documentSearcherProbe.ref)
      
      parent ! SearchDocumentSet(documentSetId, searchTerms, testActor)
      parent ! DocumentSearcherDone
      
      expectMsg(JobDone)
    }
    
    "set SearchResult state to Complete when receiving Done from Document Searcher" in new SearchHandlerContext {
      
      val searchHandler = TestActorRef(new TestSearchHandler(searchExists = false, testActor))
      val searchHandlerWatcher = TestProbe()
      
      searchHandlerWatcher watch searchHandler
      
      searchHandler ! SearchDocumentSet(documentSetId, searchTerms, testActor)
      searchHandler ! DocumentSearcherDone

      val storage = searchHandler.underlyingActor.storage
      
      there was one(storage).completeSearch(1l, documentSetId, query)
      searchHandlerWatcher.expectMsgType[Terminated]
    }
    
    "set SearchResultState to Error when receiving Failure from Document Searcher" in new SearchHandlerContext {
      val searchHandler = TestActorRef(new TestSearchHandler(searchExists = false, testActor))
      val searchHandlerWatcher = TestProbe()
      
      searchHandlerWatcher watch searchHandler
      
      val error = new Exception("exception from RequestQueue")
      
      searchHandler ! SearchDocumentSet(documentSetId, searchTerms, testActor)
      searchHandler ! Failure(error)
      
      val storage = searchHandler.underlyingActor.storage
      
      there was one(storage).failSearch(1l, documentSetId, query)
      searchHandlerWatcher.expectMsgType[Terminated]
    }
  }

}