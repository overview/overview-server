package org.overviewproject.jobhandler.documentset

import scala.concurrent.Promise
import akka.actor._
import akka.testkit.{TestActorRef, TestProbe}
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.{SearchHit, SearchHits}
import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
import org.overviewproject.jobhandler.documentset.SearchSaverProtocol.SaveIds
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.test.ForwardingActor

class SearchIndexSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "SearchIndexSearcher" should {

    val searchId: Long = 1
    val documentSetId: Long = 10
    val query: String = "query string"

    trait MockComponents {
      val startScrollId = "scrollId"
      val nextScrollId = "scrollId1"

      val startSearchPromise = Promise[SearchResponse]
      val searchIndex = mock[SearchIndex]

      searchIndex.startSearch(anyString, anyString) returns startSearchPromise.future

      val searchResultPromise1 = Promise[SearchResponse]
      val searchResultPromise2 = Promise[SearchResponse]

      searchIndex.getNextSearchResultPage(anyString) returns (searchResultPromise1.future, searchResultPromise2.future)
    }

    class ParentActor(parentProbe: ActorRef, childProps: Props) extends Actor {
      val child = context.actorOf(childProps)

      def receive = {
        case msg if sender == child => parentProbe forward msg
        case msg => child forward msg
      }

    }

    class TestSearchIndexSearcher(override val searchIndex: SearchIndex, searchSaver: ActorRef) extends SearchIndexSearcher with SearcherComponents {
      override def produceSearchSaver: Actor = new ForwardingActor(searchSaver)
    }

    "start search" in new ActorSystemContext with MockComponents {
      val searchSaver = TestProbe()

      val searcher = TestActorRef(new TestSearchIndexSearcher(searchIndex, searchSaver.ref))

      searcher ! StartSearch(searchId, documentSetId, query)

      there was one(searchIndex).startSearch(s"documents_$documentSetId", query)
    }

    "retrieve all results" in new ActorSystemContext with MockComponents {
      val parentProbe = TestProbe()
      val searchSaverProbe = TestProbe()
      val ids = Array[Long](1, 2, 3)

      val parent = system.actorOf(Props(new ParentActor(parentProbe.ref, Props(new TestSearchIndexSearcher(searchIndex, searchSaverProbe.ref)))))

      parent ! StartSearch(searchId, documentSetId, query)

      completeStartSearch(startSearchPromise, startScrollId)
      completeSearchRequest(searchResultPromise1, nextScrollId, ids)
      completeSearchRequest(searchResultPromise2, nextScrollId, Array.empty)

      there was
        one(searchIndex).getNextSearchResultPage(startScrollId) andThen
        one(searchIndex).getNextSearchResultPage(nextScrollId)

      searchSaverProbe.expectMsg(SaveIds(searchId, ids))
      parentProbe.expectMsg(SearchComplete)
    }

    "notify parent if search fails and then stop" in new ActorSystemContext with MockComponents {
      val parentProbe = TestProbe()
      val searchSaverProbe = TestProbe()
      val searcherWatcher = TestProbe()

      val error = new Exception("start search failed")
      
      val searcher = TestActorRef(Props(new TestSearchIndexSearcher(searchIndex, searchSaverProbe.ref)), parentProbe.ref, "Searcher")
      searcherWatcher watch searcher

      searcher ! StartSearch(searchId, documentSetId, query)
      startSearchPromise.failure(error)

      parentProbe.expectMsg(SearchFailure(error))
      searcherWatcher.expectMsgType[Terminated]
    }

    def completeStartSearch(startPromise: Promise[SearchResponse], scrollId: String): Unit = {
      val searchResult = mock[SearchResponse]
      searchResult.getScrollId returns scrollId

      startPromise.success(searchResult)
    }

    def completeSearchRequest(resultPromise: Promise[SearchResponse], scrollId: String, ids: Array[Long]): Unit = {
      val hitList = ids.map { id =>
        val hit = mock[SearchHit]
        val source = mock[java.util.Map[String, Object]]
        source.get("id") returns id.asInstanceOf[Object]

        hit.getSource returns source

        hit
      }

      val response = mock[SearchResponse]
      val hits = mock[SearchHits]

      response.getScrollId returns scrollId
      response.getHits returns hits

      hits.hits returns hitList
      resultPromise.success(response)
    }
  }
}