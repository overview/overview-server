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

class DocumentSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "DocumentSearcher" should {

    
    abstract class SearcherContext extends ActorSystemContext with Before {
      val documentSetId = 6
      val query = "query terms"
      val expectedQuery = s"projectid:$documentSetId $query"
      
      var queryProcessor: TestProbe = _
      var documentSearcher: ActorRef = _
      
      case class QueryProcessorMessage(query: String, msg: Any)

      trait TestQueryProcessorFactory extends QueryProcessorFactory {
        override def produce(query: String, requestQueue: ActorRef): Actor = new Actor {
          def receive = {
            case msg => queryProcessor.ref ! QueryProcessorMessage(query, msg)
          }
        }
      }
      
      override def before = {
        queryProcessor = TestProbe()
        documentSearcher = TestActorRef(new DocumentSearcher(documentSetId, query, testActor) with TestQueryProcessorFactory)
      }

    }
    


    "send a query request" in new SearcherContext {

      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(1)))
    }

    "request all remaining pages after first page is received" in new SearcherContext {

      val document: Document = mock[Document]
      val documents: Seq[Document] = Seq.fill(10)(document)

      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(1)))

      documentSearcher ! SearchResult(150, 1, documents)

      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(2)))
      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(3)))
    }
  }
}