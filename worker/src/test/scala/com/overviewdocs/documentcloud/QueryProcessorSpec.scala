package com.overviewdocs.documentcloud

import akka.actor.{Actor,ActorRef,ActorSystem,Props,actorRef2Scala}
import akka.testkit.{ImplicitSender,TestActorRef,TestKitBase,TestProbe}
import java.net.URLEncoder
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.{After,Specification}
import org.specs2.time.NoTimeConversions
import scala.concurrent.Future
import scala.util.Failure

import com.overviewdocs.documentcloud.QueryProcessorProtocol.GetPage
import com.overviewdocs.http.{Client=>HttpClient,Credentials,Response=>HttpResponse}
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.util.Configuration

// Declared here because of https://issues.scala-lang.org/browse/SI-4683
// See https://groups.google.com/d/msg/specs2-users/UYw2_hsxR4Q/2fqpeIBA_c8J
trait QueryProcessorSpecBaseScope extends Scope with TestKitBase with ImplicitSender with After with Mockito {
  val query: String = "query string"
  val pageSize: Int = Configuration.getInt("page_size")
  var progressReportCount: Int = 0

  def reportProgress(part: Int, total: Int): Unit = progressReportCount += 1

  def pageQuery(pageNum: Int, query: String): String = s"https://www.documentcloud.org/api/search.json?per_page=$pageSize&page=$pageNum&q=${URLEncoder.encode(query, "UTF-8")}"

  def emptyProcessDocument(d: Document, text: String): Unit = {}

  val mockHttpClient = smartMock[HttpClient]

  def createQueryProcessor(credentials: Option[Credentials] = None, maxDocuments: Int = 1000): Actor = {
    new QueryProcessor(query, credentials, mockHttpClient)
  }

  implicit lazy val system: ActorSystem = ActorSystem()
  override def after = system.shutdown
}

class QueryProcessorSpec extends Specification with NoTimeConversions with Mockito {

  "QueryProcessor" should {
    "request query page" in new QueryProcessorSpecBaseScope {
      val queryProcessor = TestActorRef(createQueryProcessor())

      mockHttpClient.get(any, any)(any) returns Future.failed(new Exception("we don't care"))
      queryProcessor ! GetPage(5)
      there was one(mockHttpClient).get(pageQuery(5, query))
    }

    "request non-default query page" in new QueryProcessorSpecBaseScope {
      object NonStandardConfig extends Configuration {
        private val defaultConfiguration = Configuration // the object

        def getString(key: String) : String = {
          if (key == "documentcloud_url") {
            "https://foo.bar"
          } else {
            defaultConfiguration.getString(key)
          }
        }

        def getInt(key: String) : Int = defaultConfiguration.getInt(key)
      }

      override def createQueryProcessor(credentials: Option[Credentials] = None, maxDocuments: Int = 1000): Actor = {
        new QueryProcessor(query, credentials, mockHttpClient, configuration = NonStandardConfig)
      }

      val queryProcessor = TestActorRef(createQueryProcessor())
      
      mockHttpClient.get(any, any)(any) returns Future.failed(new Exception("we don't care"))
      queryProcessor ! GetPage(5)
      there was one(mockHttpClient).get(s"https://foo.bar/api/search.json?per_page=$pageSize&page=5&q=query+string")
    }

    "send authenticated request if credentials are provided" in new QueryProcessorSpecBaseScope {
      val credentials = Credentials("username", "password")
      val queryProcessor = TestActorRef(createQueryProcessor(Some(credentials)))

      mockHttpClient.get(any, any)(any) returns Future.failed(new Exception("we don't care"))
      queryProcessor ! GetPage(5)
      there was one(mockHttpClient).get(pageQuery(5, query), credentials)
    }
    

    "send query result to parent" in new QueryProcessorSpecBaseScope {
      val numberOfDocuments = 2
      val result = jsonSearchResultPage(numberOfDocuments, 1, numberOfDocuments)

      val parent = TestProbe()
      val queryProcessor = TestActorRef(Props(createQueryProcessor()), parent.ref, "QueryProcess")

      mockHttpClient.get(any, any)(any) returns Future.successful(HttpResponse(200, result.getBytes, Map("foo" -> Seq("bar"))))
      queryProcessor ! GetPage(1)

      val r = parent.expectMsgType[SearchResult]
      r.total must be equalTo(numberOfDocuments)
      r.page must be equalTo(1)
      r.documents must haveSize(numberOfDocuments)
    }
    
    "send query failure to parent" in new QueryProcessorSpecBaseScope {
      val parent = TestProbe()

      val error = new Exception("Exception from RequestQueue")
      mockHttpClient.get(any, any)(any) returns Future.failed(error)

      val queryProcessor = TestActorRef(Props(createQueryProcessor()), parent.ref, "QueryProcess")
      queryProcessor ! GetPage(1)
      
      parent.expectMsg(Failure(error))
    }
  }

  private def jsonSearchResultPage(total: Int, page: Int, numberOfDocuments: Int): String = {
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

    s"""{
      "total": $total,
      "page": $page,
      "per_page": ${Configuration.getInt("page_size")},
      "q": "Query",
      "documents": $jsonDocumentArray 
    }"""
  }
}
