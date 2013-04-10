package org.overviewproject.documentcloud

import java.net.URLEncoder

import org.overviewproject.documentcloud.QueryProcessorProtocol.Query
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol.AddToFront
import org.specs2.mutable.{After, Specification}
import org.specs2.time.NoTimeConversions

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}


class QueryProcessorSpec extends Specification  with NoTimeConversions {

  abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with After {
    def after = system.shutdown()
  }
  
  "QueryProcessor" should {

    "request query result pages" in new ActorSystemContext {
      val query = "query string"
      val page1Query =  s"https://www.documentcloud.org/api/search.json?per_page=20&page=1&q=${URLEncoder.encode(query, "UTF-8")}"
      val queryProcessor = TestActorRef(new QueryProcessor(testActor))

      queryProcessor ! Query(query)
      
      expectMsg(AddToFront(PublicRequest(page1Query)))
    }

    "spawn actors and send them query results" in {

    }
  }
}