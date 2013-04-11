package org.overviewproject.documentcloud

import java.net.URLEncoder

import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{Start => StartRetriever}
import org.overviewproject.documentcloud.QueryProcessorProtocol.Start
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.{ActorSystemContext, TestSimpleResponse}
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit.TestActorRef



class ReportingActor(d: Document, receiver: ActorRef) extends Actor {
  def receive = {
    case  StartRetriever() => receiver ! "got message"
  }
}

class SilentActor(d: Document) extends Actor {
  def receive = { 
    case _ => 
  }
}

class QueryProcessorSpec extends Specification with NoTimeConversions {

  "QueryProcessor" should {

    "request query result pages" in new ActorSystemContext {
      def pageQuery(pageNum: Int, query: String): String = s"https://www.documentcloud.org/api/search.json?per_page=20&page=$pageNum&q=${URLEncoder.encode(query, "UTF-8")}"
      val query = "query string"
      val page1Result = jsonSearchResultPage(20, 1, 20)
      val page2Result = jsonSearchResultPage(20, 2, 10)

      val queryProcessor = TestActorRef(new QueryProcessor(query, testActor, new SilentActor(_)))

      queryProcessor ! Start()
      expectMsg(AddToFront(PublicRequest(pageQuery(1, query))))

      queryProcessor ! Result(TestSimpleResponse(200, page1Result))
      expectMsg(AddToFront(PublicRequest(pageQuery(2, query))))

      queryProcessor ! Result(TestSimpleResponse(200, page2Result))
      expectNoMsg
    }

    "spawn actors and send them query results" in new ActorSystemContext {
      val query = "query string"
      val numberOfDocuments = 10
      val result = jsonSearchResultPage(20, 1, numberOfDocuments)
      val queryProcessor = system.actorOf(Props(new QueryProcessor(query, testActor, new ReportingActor(_, testActor))))

      queryProcessor ! Start()
      queryProcessor ! Result(TestSimpleResponse(200, result))

      receiveN(numberOfDocuments)
    }
    
    "complete promise when all documents have been processed" in new ActorSystemContext {
      
    }
  }

  def jsonSearchResultPage(total: Int, page: Int, numberOfDocuments: Int): String = {
    def documentJson(page: Int, index: Int): String = {
      val id = s"$page-$index"

      s"""
      {
        "id": "dcid-$id",
        "title": "title-$id",
        "access": "public",
        "pages": 1,
        "canonical_url": "http://url-$id"
      }
      """
    }

    val documents = Seq.tabulate(numberOfDocuments)(n => documentJson(page, n))
    val jsonDocumentArray = documents.mkString("[", ",", "]")

    s"""
      {
        "total": $total,
        "page": $page,
        "per_page": 20,
        "q": "Query",
        "documents": $jsonDocumentArray 
      }"""
  }
}