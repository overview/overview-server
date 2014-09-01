package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import scala.concurrent.Promise

import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
import org.overviewproject.jobhandler.documentset.SearchSaverProtocol.SaveIds
import org.overviewproject.test.ActorSystemContext

class SearchIndexSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "SearchIndexSearcher" should {

    val searchId: Long = 1
    val documentSetId: Long = 10
    val query: String = "query string"

    trait MockComponents {
      val searchPromise = Promise[Seq[Long]]
      val searchIndex = mock[SearchIndexComponent]

      searchIndex.searchForIds(anyLong, anyString) returns searchPromise.future
    }

    class ParentActor(parentProbe: ActorRef, childProps: Props) extends Actor {
      val child = context.actorOf(childProps, "child")

      def receive = {
        case msg if sender == child => parentProbe forward msg
        case msg => child forward msg
      }

    }

    class FakeSearchSaver(msgMonitor: ActorRef) extends Actor {
      def receive = {
        case SaveIds(searchId, ids) => msgMonitor ! SaveIds(searchId, ids)
      }
    }

    class TestSearchIndexSearcher(override val searchIndex: SearchIndexComponent, searchSaver: ActorRef) extends SearchIndexSearcher with SearcherComponents {
      override def produceSearchSaver: Actor = new FakeSearchSaver(searchSaver)
    }

    "search" in new ActorSystemContext with MockComponents {
      val searchSaver = TestProbe()

      val searcher = TestActorRef(new TestSearchIndexSearcher(searchIndex, searchSaver.ref))

      searcher ! StartSearch(searchId, documentSetId, query)

      there was one(searchIndex).searchForIds(documentSetId, query)
    }

    "retrieve all results" in new ActorSystemContext with MockComponents {
      val parentProbe = TestProbe()
      val parentMonitor = TestProbe()

      val searchSaverProbe = TestProbe()
      val ids = Seq[Long](1, 2, 3)

      val parent = system.actorOf(
        Props(new ParentActor(parentProbe.ref,
          Props(new TestSearchIndexSearcher(searchIndex, searchSaverProbe.ref)))), "Parent")

      parent ! StartSearch(searchId, documentSetId, query)

      searchPromise.success(ids)

      searchSaverProbe.expectMsg(SaveIds(searchId, ids))

      parentProbe.expectMsg(SearchComplete)
    }

    "notify parent if search fails and then stop" in new ActorSystemContext with MockComponents {
      val parentProbe = TestProbe()
      val searchSaverProbe = TestProbe()
      val searcherWatcher = TestProbe()

      val error = new Exception("test failure")

      val searcher = TestActorRef(Props(new TestSearchIndexSearcher(searchIndex, searchSaverProbe.ref)), parentProbe.ref, "Searcher")
      searcherWatcher watch searcher

      searcher ! StartSearch(searchId, documentSetId, query)
      searchPromise.failure(error)

      parentProbe.expectMsg(SearchFailure(error))
      searcherWatcher.expectMsgType[Terminated]
    }
  }
}
