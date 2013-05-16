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

class DocumentSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "DocumentSearcher" should {

    
    trait TestConfig {
      val maxDocuments: Int = Configuration.maxDocuments
      val pageSize: Int = Configuration.pageSize
    }
    
    trait NotAllPagesNeeded extends TestConfig {
      val pagesNeeded: Int = 2
      override val pageSize: Int = 5
      override val maxDocuments: Int = pageSize * pagesNeeded 
      
      val totalDocuments = 10 * maxDocuments 
    }
    
    abstract class SearcherContext extends ActorSystemContext with Before {
      this: TestConfig  => 
        
      val documentSetId = 6
      val queryTerms = "query terms"
      val expectedQuery = s"projectid:$documentSetId $queryTerms"
      
      
      var queryProcessor: TestProbe = _
      var documentSearcher: TestActorRef[TestDocumentSearcher] = _
      
      case class QueryProcessorMessage(query: String, msg: Any)

      trait TestQueryProcessorFactory extends QueryProcessorFactory {
        var query: Option[String] = None
        
        override def produce(queryString: String, requestQueue: ActorRef): Actor = {
          query = Some(queryString)  
        
          new Actor {
            def receive = {
              case msg => queryProcessor.ref forward msg
            }
          }
        }
      }
      
      type TestDocumentSearcher = DocumentSearcher with TestQueryProcessorFactory
      
      override def before = {
        queryProcessor = TestProbe()
        documentSearcher = 
          TestActorRef(new DocumentSearcher(documentSetId, queryTerms, testActor, pageSize, maxDocuments) with TestQueryProcessorFactory)
      }

    }
     abstract class SearcherSetup extends SearcherContext with TestConfig

    "send a query request" in new SearcherSetup {
      documentSearcher.underlyingActor.query must beSome(expectedQuery)
      queryProcessor.expectMsg(GetPage(1))
    }

    "request all remaining pages after first page is received" in new SearcherSetup {
      val document: Document = mock[Document]
      val documents: Seq[Document] = Seq.fill(10)(document)

      documentSearcher ! SearchResult(150, 1, documents)
      
      val messages = Seq.tabulate(3)(p => GetPage(p + 1))
      val receivedMessages = queryProcessor.receiveN(3)
      receivedMessages must haveTheSameElementsAs(messages)
    }
    
    "only request pages needed to get maxDocuments results" in new SearcherContext with NotAllPagesNeeded {
      
      val document: Document = mock[Document]
      val documents: Seq[Document] = Seq.fill(10)(document)

      documentSearcher ! SearchResult(totalDocuments, 1, documents)
      
      queryProcessor.receiveN(pagesNeeded)
      queryProcessor.expectNoMsg(100 millis)

      
    }
  }
}