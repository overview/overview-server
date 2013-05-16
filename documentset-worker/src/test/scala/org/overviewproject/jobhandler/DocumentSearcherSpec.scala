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

class DocumentSearcherSpec extends Specification with NoTimeConversions with Mockito {

  "DocumentSearcher" should {

    "send a query request" in new ActorSystemContext {

      val queryProcessor = TestProbe()

      case class QueryProcessorMessage(query: String, msg: Any)
      
      trait TestQueryProcessorFactory extends QueryProcessorFactory {
        override def produce(query: String, requestQueue: ActorRef): Actor = new Actor {
          def receive = {
            case msg => queryProcessor.ref ! QueryProcessorMessage(query, msg)
          }
        }
      }

      val documentSetId = 6
      val query = "query terms"
      val expectedQuery = s"projectid:$documentSetId $query"
      
      val documentSearcher = TestActorRef(new DocumentSearcher(documentSetId, query, testActor) with TestQueryProcessorFactory)

      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(1)))
    }
  }
  
  "request all remaining pages after first page is received" in new ActorSystemContext {
      val queryProcessor = TestProbe()

      case class QueryProcessorMessage(query: String, msg: Any)
      
      trait TestQueryProcessorFactory extends QueryProcessorFactory {
        override def produce(query: String, requestQueue: ActorRef): Actor = new Actor {
          def receive = {
            case msg => queryProcessor.ref ! QueryProcessorMessage(query, msg)
          }
        }
      }

      val documentSetId = 6
      val query = "query terms"
      val expectedQuery = s"projectid:$documentSetId $query"
      
      val documentSearcher = TestActorRef(new DocumentSearcher(documentSetId, query, testActor) with TestQueryProcessorFactory)

      val document: Document = mock[Document]
      val documents: Seq[Document] = Seq.fill(10)(document)
      
      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(1)))
      
      documentSearcher ! SearchResult(150, 1, documents)
      
      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(2)))
      queryProcessor.expectMsg(QueryProcessorMessage(expectedQuery, GetPage(3)))

  }
}