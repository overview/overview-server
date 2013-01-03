/*
 * BulkHttpRetrieverSpec
 *
 * Overview Project
 * Created by Jonas Karlsson, September 2012
 */
package org.overviewproject.http

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
import org.overviewproject.clustering.DCDocumentAtURL
import scala.collection.JavaConversions._

/**
 * A dummy Response to a http request.
 * Will be expanded to have actual test data in body and headers.
 */
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

/**
 * A mock AsyncHttpRetriever for testing. Provides an actor system with an execution context.
 * Subclass to respond to requests in ways appropriate for your test.
 */
abstract class TestHttpRetriever extends AsyncHttpRetriever {

  val actorSystem = ActorSystem("TestActorSystem") // should probably be in separate trait

  def request(resource: DocumentAtURL, onSuccess: Response => Unit, onFailure: Throwable => Unit)

  /** Will be expanded once needed by tests */
  def blockingHttpRequest(url: String): String = url
  val executionContext: ExecutionContext = actorSystem.dispatcher
}

/** Trait to be mixed in with test contexts that need a TestHttpRetriever */
trait RetrieverProvider {
  val retriever: TestHttpRetriever
}

/** Provides a TestRetriever that responds successfully to all requests */
trait SuccessfulRetriever extends RetrieverProvider {
  val retriever = new TestHttpRetriever {
    override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
      onFailure: Throwable => Unit) {
      val response = new TestResponse
      onSuccess(response)
    }
  }
}

/** Provides a TestRetriever that responds with an Exception to all requests */
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

    /**
     * Context for counting number of processed requests to the AsyncHttpRetriever
     */
    trait SimulatedWebClient extends After {
      this: RetrieverProvider =>
      var requestsProcessed = new scala.collection.mutable.ArrayBuffer[String]
      val bulkHttpRetriever = new BulkHttpRetriever[DCDocumentAtURL](retriever)
      val urlsToRetrieve = Seq.fill(10)(new DCDocumentAtURL("title", "id", "url"))

      def processDocument(document: DCDocumentAtURL, result: String): Boolean = {
        requestsProcessed += result
        true
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
