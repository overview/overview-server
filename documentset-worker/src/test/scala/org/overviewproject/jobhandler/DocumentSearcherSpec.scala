package org.overviewproject.jobhandler

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.actor._
import akka.testkit._
import akka.actor.ActorDSL._
import org.overviewproject.documentcloud.QueryProcessorProtocol._
import org.overviewproject.documentcloud.{ Document, SearchResult }
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import org.specs2.mutable.Before
import org.specs2.specification.Scope
import org.overviewproject.documentcloud.QueryProcessor
import org.overviewproject.util.Configuration
import org.overviewproject.jobhandler.SearchSaverProtocol._

class DocumentSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "DocumentSearcher" should {

    trait TestConfig {
      val maxDocuments: Int = Configuration.maxDocuments
      val pageSize: Int = Configuration.pageSize
      val documentSetId = 6
      val queryTerms = "query terms"
      val expectedQuery = s"projectid:$documentSetId $queryTerms"
      
      val document: Document = mock[Document]
      val documents: Seq[Document] = Seq.fill(10)(document)
    }

    trait NotAllPagesNeeded extends TestConfig {
      val pagesNeeded: Int = 2
      override val pageSize: Int = 5
      override val maxDocuments: Int = pageSize * pagesNeeded  

      val totalDocuments = 10 * maxDocuments
    }

    trait NotAllDocumentsInLastPageNeeded extends NotAllPagesNeeded {
      override val maxDocuments: Int = pageSize + 1
    }
    
    class ForwardingActor(target: ActorRef) extends Actor {
      def receive = {
        case m => target forward m
      }
    }

    abstract class SearcherContext extends ActorSystemContext {
      this: TestConfig =>

      trait TestComponents extends DocumentSearcherComponents {
        val queryProcessorTarget: ActorRef
        val searchSaverTarget: ActorRef
        var queryString: Option[String]

        def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor = {
          queryString = Some(query)
          new ForwardingActor(queryProcessorTarget)
        }
        def produceSearchSaver: Actor = new ForwardingActor(searchSaverTarget)
      }

      class TestDocumentSearcher(documentSetId: Long, queryTerms: String, queueProbe: ActorRef,
        queryProcessorProbe: ActorRef, searchSaverProbe: ActorRef) extends {
        
        val queryProcessorTarget: ActorRef = queryProcessorProbe
        val searchSaverTarget: ActorRef = searchSaverProbe
        var queryString: Option[String] = None

      } with DocumentSearcher(documentSetId, queryTerms, queueProbe, pageSize, maxDocuments) with TestComponents

      def createDocumentSearcher(documentSetId: Long, queryTerms: String, queueProbe: ActorRef,
          queryProcessorProbe: ActorRef, searchSaverProbe: ActorRef): TestActorRef[TestDocumentSearcher] =
          TestActorRef(new TestDocumentSearcher(documentSetId, queryTerms, testActor,
            queryProcessorProbe, searchSaverProbe))
    }

    abstract class SearcherSetup extends SearcherContext with TestConfig

    "send a query request" in new SearcherSetup {

      val queryProcessor = TestProbe()
      val searchSaver = TestProbe()

      val documentSearcher = createDocumentSearcher(documentSetId, queryTerms, testActor, 
        queryProcessor.ref, searchSaver.ref)

      documentSearcher.underlyingActor.queryString must beSome(expectedQuery)
      queryProcessor.expectMsg(GetPage(1))
    }

    "request all remaining pages after first page is received" in new SearcherSetup {
      val queryProcessor = TestProbe()
      val searchSaver = TestProbe()

      val documentSearcher = createDocumentSearcher(documentSetId, queryTerms, testActor,
        queryProcessor.ref, searchSaver.ref)

      documentSearcher ! SearchResult(150, 1, documents)

      val messages = Seq.tabulate(3)(p => GetPage(p + 1))
      val receivedMessages = queryProcessor.receiveN(3)
      receivedMessages must haveTheSameElementsAs(messages)
    }

    "only request pages needed to get maxDocuments results" in new SearcherContext with NotAllPagesNeeded {
      val queryProcessor = TestProbe()
      val searchSaver = TestProbe()

      val documentSearcher = createDocumentSearcher(documentSetId, queryTerms, testActor,
        queryProcessor.ref, searchSaver.ref)

      documentSearcher ! SearchResult(totalDocuments, 1, documents)

      queryProcessor.receiveN(pagesNeeded)
      queryProcessor.expectNoMsg(100 millis)
    }
    
    "tell search saver to save documents in result" in new SearcherSetup {
      val queryProcessor = TestProbe()
      val searchSaver = TestProbe()

      val documentSearcher = createDocumentSearcher(documentSetId, queryTerms, testActor,
        queryProcessor.ref, searchSaver.ref)

      documentSearcher ! SearchResult(100, 1, documents)
      searchSaver.expectMsg(Save(documents))
    }
    
    "only send search saver maxDocuments documents" in new SearcherContext with NotAllDocumentsInLastPageNeeded {
      val queryProcessor = TestProbe()
      val searchSaver = TestProbe()

      val documentSearcher = createDocumentSearcher(documentSetId, queryTerms, testActor,
        queryProcessor.ref, searchSaver.ref)
      
      documentSearcher ! SearchResult(100, 1, documents)
      searchSaver.expectMsg(Save(documents))

      documentSearcher ! SearchResult(100, 2, documents)
      searchSaver.expectMsg(Save(documents.take(1)))

    }
  }
}