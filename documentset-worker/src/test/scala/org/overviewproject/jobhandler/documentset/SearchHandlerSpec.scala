package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mutable.Before

class SearchHandlerSpec extends Specification with Mockito {

  "SearchHandler" should {

    abstract class MockStorageSearchHandler(searchExists: Boolean) extends SearchHandler with SearchHandlerComponents {
      override val storage = mock[Storage]

      storage.searchExists(anyLong, anyString) returns searchExists
      storage.createSearchResult(anyLong, anyString) returns 1l
      storage.queryForProject(anyLong, anyString) returns "search terms"

    }

    class TestSearchHandler(searchExists: Boolean, documentSearcherProbe: ActorRef) extends MockStorageSearchHandler(searchExists) {
      val actorCreator = new ActorCreator { // can't mock creation of actors
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new ForwardingActor(documentSearcherProbe)
      }
    }

    class SearchHandlerWithSearchFailure extends MockStorageSearchHandler(false) {

      val actorCreator = new ActorCreator { // can't mock creation of actors
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new Actor {
            def receive = {
              case _ => context.stop(self) // FIXME: throwing exception produces ugly output
            }
          }
      }
    }

    class SearchHandlerParent(searchExists: Boolean, parentProbe: ActorRef, documentSearcherProbe: ActorRef) extends Actor {
      val searchHandler = context.actorOf(Props(new TestSearchHandler(searchExists, documentSearcherProbe)))

      def receive = {
        case msg if sender == searchHandler => parentProbe forward msg
        case msg => searchHandler forward msg
      }

    }

    trait SearchExistence {
      def searchExists: Boolean
    }

    trait ExistingSearch extends SearchExistence {
      override def searchExists: Boolean = true
    }

    trait NoExistingSearch extends SearchExistence {
      override def searchExists: Boolean = false
    }

    trait SearchInfo {
      val documentSetId = 5l
      val searchTerms = "search terms"
    }

    abstract class SearchHandlerWithParent extends ActorSystemContext with SearchInfo with Before {
      self: SearchExistence =>

      var parent: ActorRef = _
      var documentSearcher: TestProbe = _

      def before = {
        documentSearcher = TestProbe()
        parent = TestActorRef(new SearchHandlerParent(searchExists, testActor, documentSearcher.ref))

      }
    }

    abstract class MonitoredSearchHandler extends ActorSystemContext with SearchInfo with Before {
      var searchHandler: TestActorRef[MockStorageSearchHandler] = _
      var monitor: TestProbe = _
      var documentSearcher: TestProbe = _

      var storage: MockStorageSearchHandler#Storage = _

      def before = {
        monitor = TestProbe()

        searchHandler = TestActorRef(Props(createSearchHandler), monitor.ref, "SearchHandler")
        storage = searchHandler.underlyingActor.storage

        monitor watch searchHandler
      }

      protected def createSearchHandler: MockStorageSearchHandler = {
        documentSearcher = TestProbe()

        new TestSearchHandler(searchExists = false, documentSearcher.ref)
      }
    }

    abstract class MonitoredFailingSearchHandler extends MonitoredSearchHandler {
      override protected def createSearchHandler: MockStorageSearchHandler = {
        new SearchHandlerWithSearchFailure
      }
    }

    "send JobDone to parent if SearchResult already exists" in new SearchHandlerWithParent with ExistingSearch {
      parent ! SearchDocumentSet(documentSetId, searchTerms)

      expectMsg(JobDone(documentSetId))
    }

    "create a new SearchResult and start Searcher if SearchResult doesn't exist" in new SearchHandlerWithParent with NoExistingSearch {
      parent ! SearchDocumentSet(documentSetId, searchTerms)

      documentSearcher.expectMsg(StartSearch(1l, documentSetId, searchTerms))
    }

    "send JobDone to parent when receiving SearchComplete from Searcher" in new SearchHandlerWithParent with NoExistingSearch {
      parent ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcher.expectMsgType[StartSearch]

      parent ! SearchComplete

      expectMsg(JobDone(documentSetId))
    }

    "set SearchResult state to Complete when receiving SearchComplete from Searcher" in new MonitoredSearchHandler {
      searchHandler ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcher.expectMsgType[StartSearch]

      searchHandler ! SearchComplete

      monitor.expectMsg(JobDone(documentSetId))
      monitor.expectMsgType[Terminated]
      there was one(storage).completeSearch(1l, documentSetId, searchTerms)
    }

    "set SearchResultState to Error when receiving SearchFailure from Searcher" in new MonitoredSearchHandler {
      val error = new Exception("exception from RequestQueue")

      searchHandler ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcher.expectMsgType[StartSearch]

      searchHandler ! SearchFailure(error)

      monitor.expectMsg(JobDone(documentSetId))
      monitor.expectMsgType[Terminated]
      there was one(storage).failSearch(1l, documentSetId, searchTerms)
    }

    "set SearchResultState to Error if Searcher dies unexpectedly" in new MonitoredFailingSearchHandler {
      searchHandler ! SearchDocumentSet(1l, "search terms")

      monitor.expectMsg(JobDone(1l))
      there was one(storage).failSearch(1l, 1l, "search terms")
      monitor.expectTerminated(searchHandler)
    }
  }

}