package org.overviewproject.jobhandler

import scala.language.postfixOps

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.actor._
import akka.testkit._
import akka.actor.ActorDSL._
import org.overviewproject.documentcloud.QueryProcessorProtocol._

class DocumentSearcherSpec extends Specification {

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
}