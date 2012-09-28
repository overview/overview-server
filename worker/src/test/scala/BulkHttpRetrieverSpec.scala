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
import org.specs2.specification.Scope
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

class TestHttpRetriever extends AsyncHttpRetriever {

  val actorSystem = ActorSystem("TestActorSystem")

  def request(resource: DocumentAtURL, onSuccess: Response => Unit,
    onFailure: Throwable => Unit) {

    val response = new TestResponse
    onSuccess(response)
  }

  def blockingHttpRequest(url: String): String = url

  val executionContext: ExecutionContext = actorSystem.dispatcher

}

class BulkHttpRetrieverSpec extends Specification {

  "BulkHttpRetriever" should {

    trait SimulatedWebClient extends Scope {
      var requestsProcessed = new scala.collection.mutable.ArrayBuffer[String]
      val retriever = new TestHttpRetriever
      val bulkHttpRetriever = new BulkHttpRetriever[DCDocumentAtURL](retriever)
      val urlsToRetrieve = Seq.fill(10)(new DCDocumentAtURL("title", "id", "url"))

      def processDocument(document: DCDocumentAtURL, result: String) {
	requestsProcessed += result
      }

      implicit val actorSystem = retriever.actorSystem
    }

    "retrieve and process documents" in new SimulatedWebClient {
      val done = bulkHttpRetriever.retrieve(urlsToRetrieve, processDocument)
      val requestsWithErrors = Await.result(done, Timeout.never.duration)

      requestsWithErrors must beEmpty
      requestsProcessed must have size (urlsToRetrieve.size)

    }
  }

}
