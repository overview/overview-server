package overview.http

import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.dispatch.ExecutionContext
import akka.util.Timeout
import com.ning.http.client.Cookie
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.ning.http.client.Response
import java.net.URI
import org.specs2.mutable.Specification
import org.specs2.specification.After
import overview.clustering.DCDocumentAtURL
import scala.collection.JavaConversions._

class TestResponse extends Response {
  def getContentType: String = "content-type"
  def getCookies = Seq()
  def getHeader(name: String): String = "header"
  def getHeaders: FluentCaseInsensitiveStringsMap = new FluentCaseInsensitiveStringsMap()
  def getHeaders(name: String) = Seq()
  def getResponseBody: String = "body"
  def getResponseBody(charset: String): String = "body"
  def getResponseBodyAsBytes = null
  def getResponseBodyAsStream = null
  def getResponseBodyExcerpt(maxLength: Int): String = "excerpt"
  def getResponseBodyExcerpt(maxLength: Int, charset: String) = null
  def getStatusCode: Int = 400
  def getStatusText: String = "OK"
  def getUri: URI = new URI("uri")
  def hasResponseBody: Boolean = true
  def hasResponseHeaders: Boolean = true
  def hasResponseStatus: Boolean = true
  def isRedirected: Boolean = false
  override def toString: String = "TestResponse"
}

abstract class TestHttpRetriever extends AsyncHttpRetriever {

  val actorSystem = ActorSystem("TestActorSystem")

  def request(resource: DocumentAtURL, onSuccess: Response => Unit, onFailure: Throwable => Unit)

  def blockingHttpRequest(url: String): String = url
  val executionContext: ExecutionContext = actorSystem.dispatcher
}

trait RetrieverProvider {
  val retriever: TestHttpRetriever
}

trait SuccessfulRetriever extends RetrieverProvider {
  val retriever = new TestHttpRetriever {
    override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
      onFailure: Throwable => Unit) {
      val response = new TestResponse
      onSuccess(response)
    }
  }
}

trait FailingRetriever extends RetrieverProvider {
  val retriever = new TestHttpRetriever {
    override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
      onFailure: Throwable => Unit) {
      onFailure(new Exception("failed request"))
    }
  }
}

class BulkHttpRetrieverSpec extends Specification {

  "BulkHttpRetriever" should {

    trait SimulatedWebClient extends After {
      this: RetrieverProvider =>
      var requestsProcessed = new scala.collection.mutable.ArrayBuffer[String]
      val bulkHttpRetriever = new BulkHttpRetriever[DCDocumentAtURL](retriever)
      val urlsToRetrieve = Seq.fill(10)(new DCDocumentAtURL("title", "id", "url"))

      def processDocument(document: DCDocumentAtURL, result: String) {
        requestsProcessed += result
      }

      implicit def actorSystem = retriever.actorSystem

      def after = actorSystem.shutdown

    }

    trait SuccessfulRequests extends SuccessfulRetriever with SimulatedWebClient
    trait FailingRequests extends FailingRetriever with SimulatedWebClient

    "retrieve and process documents" in new SuccessfulRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must beEmpty
      requestsProcessed must have size (urlsToRetrieve.size)
    }

    "collect failed requests" in new FailingRequests {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must have size (urlsToRetrieve.size)
    }

  }

}
