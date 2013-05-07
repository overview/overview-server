package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{GetTextSucceeded, JobComplete, Start => StartRetriever}
import org.overviewproject.documentcloud.QueryProcessorProtocol.GetPage
import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol.{AddToFront, Result}
import org.overviewproject.test.{ActorSystemContext, TestSimpleResponse}
import org.overviewproject.util.Configuration
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import akka.testkit.{TestActorRef, TestProbe}
import org.specs2.mutable.Before


class QueryProcessorSpec extends Specification with NoTimeConversions {

  "QueryProcessor" should {

    abstract class QueryContext extends ActorSystemContext with Before {
      val query = "query string"
      val pageSize = Configuration.pageSize
      
      var progressReportCount: Int = 0
      
      override def before = {} // without this method, actor creation fails

      def reportProgress(part: Int, total: Int): Unit = progressReportCount += 1

      def pageQuery(pageNum: Int, query: String): String = s"https://www.documentcloud.org/api/search.json?per_page=$pageSize&page=$pageNum&q=${URLEncoder.encode(query, "UTF-8")}"

      def emptyProcessDocument(d: Document, text: String): Unit = {}
      def createQueryProcessor(credentials: Option[Credentials] = None, maxDocuments: Int = 1000): Actor =
        new QueryProcessor(query, credentials, testActor)
    }
    
    "request query page" in new QueryContext {
      val pageNum = 5
      val queryProcessor = TestActorRef(createQueryProcessor())
      
      queryProcessor ! GetPage(pageNum)
      expectMsg(AddToFront(PublicRequest(pageQuery(pageNum, query))))
      
    }

    "send authenticated request if credentials are provided" in new QueryContext {
      val pageNum = 5
      val credentials = Credentials("username", "password")
      val queryProcessor = TestActorRef(createQueryProcessor(Some(credentials)))

      queryProcessor ! GetPage(pageNum)
      expectMsg(AddToFront(PrivateRequest(pageQuery(pageNum, query), credentials)))
    }
    

    "send query result to parent" in new QueryContext {
      val numberOfDocuments = 10
      val result = jsonSearchResultPage(numberOfDocuments, 1, numberOfDocuments)
      val parent = TestProbe()
      val queryProcessor = TestActorRef(Props(createQueryProcessor()), parent.ref, "QueryProcess")

      queryProcessor ! Result(TestSimpleResponse(200, result))

      val r = parent.expectMsgType[SearchResult]
      r.total must be equalTo(numberOfDocuments)
      r.page must be equalTo(1)
      r.documents must haveSize(numberOfDocuments)
      
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
        "canonical_url": "http://url-$id",
        "resources": {
          "text": "http://url-$id.txt",
          "page": {
            "text": "http://url-$id-p{page}"
          }
        }
      }
      """
    }

    val documents = Seq.tabulate(numberOfDocuments)(n => documentJson(page, n))
    val jsonDocumentArray = documents.mkString("[", ",", "]")

    s"""
      {
        "total": $total,
        "page": $page,
        "per_page": ${Configuration.pageSize},
        "q": "Query",
        "documents": $jsonDocumentArray 
      }"""
  }
}