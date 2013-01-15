package org.overviewproject.http

import java.net.URI
import scala.collection.JavaConversions._
import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import com.ning.http.client.{FluentCaseInsensitiveStringsMap, Response}

/**
 * A dummy Response to a http request.
 * Will be expanded to have actual test data in body and headers.
 */
class TestResponse(statusCode: Int = 200, status: String = "OK") extends Response {
  def getContentType: String = "content-type"
  def getCookies = Seq()
  def getHeader(name: String): String = "header"
  def getHeaders: FluentCaseInsensitiveStringsMap = TestResponse.headers
  def getHeaders(name: String) = Seq()
  def getResponseBody: String = "body"
  def getResponseBody(charset: String): String = "body"
  def getResponseBodyAsBytes = null
  def getResponseBodyAsStream = null
  def getResponseBodyExcerpt(maxLength: Int): String = "excerpt"
  def getResponseBodyExcerpt(maxLength: Int, charset: String) = null
  def getStatusCode: Int = statusCode
  def getStatusText: String = status
  def getUri: URI = new URI("uri")
  def hasResponseBody: Boolean = true
  def hasResponseHeaders: Boolean = true
  def hasResponseStatus: Boolean = true
  def isRedirected: Boolean = false
  override def toString: String = "TestResponse"
}

object TestResponse {
  val headers: FluentCaseInsensitiveStringsMap = {
    val headers = new FluentCaseInsensitiveStringsMap()
    headers.add("header1", "value1")
    headers.add("header2", "value2")
    headers    
  }
}
/**
 * A mock AsyncHttpRetriever for testing. Provides an actor system with an execution context.
 * Subclass to respond to requests in ways appropriate for your test.
 */
abstract class TestHttpRetriever(actorSystem: ActorSystem) extends AsyncHttpRetriever {

  def request(resource: DocumentAtURL, onSuccess: Response => Unit, onFailure: Throwable => Unit)

  /** Will be expanded once needed by tests */
  def blockingHttpRequest(url: String): String = url
  val executionContext: ExecutionContext = actorSystem.dispatcher
}

/** Trait to be mixed in with test contexts that need a TestHttpRetriever */
trait RetrieverProvider {
  lazy implicit val actorSystem = ActorSystem("TestActorSystem") // should probably be in separate trait
  val retriever: TestHttpRetriever
}

/** Provides a TestRetriever that responds successfully to all requests */
trait SuccessfulRetriever extends RetrieverProvider {
  val retriever = new TestHttpRetriever(actorSystem) {
    override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
      onFailure: Throwable => Unit) {
      val response = new TestResponse
      onSuccess(response)
    }
  }
}

/** Provides a TestRetriever that responds with an Exception to all requests */
trait FailingRetriever extends RetrieverProvider {
  val retriever = new TestHttpRetriever(actorSystem) {
    override def request(resource: DocumentAtURL, onSuccess: Response => Unit,
      onFailure: Throwable => Unit) {
      onFailure(new Exception("failed request"))
    }
  }
}
