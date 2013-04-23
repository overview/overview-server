package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{ Start => StartRetriever }
import org.overviewproject.documentcloud.QueryProcessorProtocol.Start
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.{ ActorSystemContext, TestSimpleResponse }
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import akka.actor.{ Actor, ActorRef, Props }
import akka.testkit.TestActorRef
import scala.concurrent.Promise
import org.specs2.mutable.Before
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.GetTextSucceeded
import scala.util.Success
import org.overviewproject.http.Credentials
import org.overviewproject.http.PrivateRequest
import akka.testkit.TestActor
import akka.testkit.TestProbe
import org.overviewproject.util.Configuration

class ReportingActor(d: Document, receiver: ActorRef, listener: ActorRef) extends Actor {
  def receive = {
    case StartRetriever() => listener ! "got message"
  }
}

class SilentActor(d: Document, receiver: ActorRef) extends Actor {
  def receive = {
    case _ =>
  }
}

class CompletingActor(d: Document, receiver: ActorRef) extends Actor {
  def receive = {
    case StartRetriever() => receiver ! GetTextSucceeded(d, "text")
  }
}

class QueryProcessorSpec extends Specification with NoTimeConversions {

  "QueryProcessor" should {

    abstract class QueryContext extends ActorSystemContext with Before {
      val query = "query string"
      val pageSize = Configuration.pageSize
      
      var queryInformation: QueryInformation = _

      def before = {
        queryInformation = new QueryInformation
      }

      def pageQuery(pageNum: Int, query: String): String = s"https://www.documentcloud.org/api/search.json?per_page=$pageSize&page=$pageNum&q=${URLEncoder.encode(query, "UTF-8")}"

      def emptyProcessDocument(d: Document, text: String): Unit = {}
      def createQueryProcessor(receiverCreator: (Document, ActorRef) => Actor, credentials: Option[Credentials] = None, maxDocuments: Int = 1000): Actor =
        new QueryProcessor(query, queryInformation, credentials, maxDocuments, emptyProcessDocument, testActor, receiverCreator)
    }

    "request query result pages" in new QueryContext {
      val page1Result = jsonSearchResultPage(2 * pageSize, 1, pageSize)
      val page2Result = jsonSearchResultPage(2 * pageSize, 2, pageSize)

      val queryProcessor = TestActorRef(createQueryProcessor(new SilentActor(_, _)))

      queryProcessor ! Start()
      expectMsg(AddToFront(PublicRequest(pageQuery(1, query))))

      queryProcessor ! Result(TestSimpleResponse(200, page1Result))
      expectMsg(AddToFront(PublicRequest(pageQuery(2, query))))

      queryProcessor ! Result(TestSimpleResponse(200, page2Result))
      expectNoMsg
    }

    "spawn actors and send them query results" in new QueryContext {
      val numberOfDocuments = 10
      val result = jsonSearchResultPage(numberOfDocuments, 1, numberOfDocuments)
      val queryProcessor = system.actorOf(Props(createQueryProcessor(new ReportingActor(_, _, testActor))))

      queryProcessor ! Start()
      queryProcessor ! Result(TestSimpleResponse(200, result))

      receiveN(numberOfDocuments)
    }

    "complete errors promise when all documents have been processed" in new QueryContext {
      val numberOfDocuments = 5
      val result = jsonSearchResultPage(numberOfDocuments, 1, numberOfDocuments)
      val queryProcessor = system.actorOf(Props(createQueryProcessor(new CompletingActor(_, _))))

      queryProcessor ! Start()
      queryProcessor ! Result(TestSimpleResponse(200, result))

      awaitCond(queryInformation.errors.isCompleted)
    }

    "complete documentsTotal promise after first page of result has been received" in new QueryContext {
      val numberOfDocuments = 5
      val numberOfPages = 2
      val result = jsonSearchResultPage(numberOfDocuments, numberOfPages, numberOfDocuments)
      val queryProcessor = system.actorOf(Props(createQueryProcessor(new SilentActor(_, _))))

      queryProcessor ! Start()
      queryProcessor ! Result(TestSimpleResponse(200, result))

      val totalFuture = queryInformation.documentsTotal.future

      awaitCond(totalFuture.isCompleted)
      totalFuture.value must beSome(Success(numberOfDocuments))
    }

    "send authenticated request if credentials are provided" in new QueryContext {
      val credentials = Credentials("username", "password")
      val queryProcessor = TestActorRef(createQueryProcessor(new SilentActor(_, _), Some(credentials)))

      queryProcessor ! Start()
      expectMsg(AddToFront(PrivateRequest(pageQuery(1, query), credentials)))
    }
    
    "don't retrieve more than specified maximum number of documents" in new QueryContext {
      val totalDocuments = 600
      val numberOfDocuments = pageSize
      val page1Result = jsonSearchResultPage(totalDocuments, 1, numberOfDocuments)
      val page2Result = jsonSearchResultPage(totalDocuments, 2, numberOfDocuments)

      val maxDocuments = pageSize + 10 
        
      val retrieverCounter = TestProbe()
      val queryProcessor = TestActorRef(createQueryProcessor(new ReportingActor(_, _, retrieverCounter.ref), maxDocuments = maxDocuments))
      
      queryProcessor ! Start()
      receiveN(1)
      queryProcessor ! Result(TestSimpleResponse(200, page1Result))
      receiveN(1)
      queryProcessor ! Result(TestSimpleResponse(200, page2Result))      
      expectNoMsg()
      
      retrieverCounter.receiveN(maxDocuments)
      retrieverCounter.expectNoMsg()
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