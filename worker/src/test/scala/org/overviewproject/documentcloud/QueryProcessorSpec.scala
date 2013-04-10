package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.documentcloud.QueryProcessorProtocol._
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import org.specs2.mutable.{ After, Specification }
import org.specs2.time.NoTimeConversions
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import org.overviewproject.http.SimpleResponse

case class TestSimpleResponse(override val status: Int, override val body: String, simpleHeaders: Map[String, String] = Map()) extends SimpleResponse {
  override def headers(name: String): Seq[String] = simpleHeaders.get(name) match {
    case Some(value) => Seq(value)
    case _ => Seq.empty
  }
}

class QueryProcessorSpec extends Specification with NoTimeConversions {

  abstract class ActorSystemContext extends TestKit(ActorSystem()) with ImplicitSender with After {
    def after = system.shutdown()
  }

  "QueryProcessor" should {

    "request query result pages" in new ActorSystemContext {
      def pageQuery(pageNum: Int, query: String): String = s"https://www.documentcloud.org/api/search.json?per_page=20&page=$pageNum&q=${URLEncoder.encode(query, "UTF-8")}" 
      val query = "query string"
      val page1Result = jsonSearchResultPage(20, 1, 20)
      val page2Result = jsonSearchResultPage(20, 2, 10)

      val queryProcessor = TestActorRef(new QueryProcessor(testActor, query))

      queryProcessor ! Start()
      expectMsg(AddToFront(PublicRequest(pageQuery(1, query))))

      queryProcessor ! Result(TestSimpleResponse(200, page1Result))
      expectMsg(AddToFront(PublicRequest(pageQuery(2, query))))
      
      queryProcessor ! Result(TestSimpleResponse(200, page2Result))
      expectNoMsg
    }

    "spawn actors and send them query results" in {

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