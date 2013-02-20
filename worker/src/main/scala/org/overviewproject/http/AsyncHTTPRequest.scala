/**
 * AsyncHTTPRequest.scala
 *
 * Thin wrapper on Ning AsyncHTTPClient library, to manage singleton object, and provide callback and akka Future-based interfaces
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.http

import scala.concurrent.{ ExecutionContext, Future, Promise }
import com.ning.http.client._
import com.ning.http.client.Realm.AuthScheme
import com.ning.http.client.{ Response => AHCResponse }
import scala.io.Source._
import org.overviewproject.util.Logger

// Single object that interfaces to Ning's Async HTTP library
class AsyncHttpRequest extends AsyncHttpRetriever {

  // expose this type so that users don't need to import com.ning.http.client
  type Response = AHCResponse

  private def getHttpConfig = {
    val builder = new AsyncHttpClientConfig.Builder()
    val config = builder
      .setFollowRedirects(true)
      .setCompressionEnabled(true)
      .setAllowPoolingConnection(true)
      .setRequestTimeoutInMs(5 * 60 * 1000) // 5 minutes, to allow for downloading large files
      .build
    config
  }

  private lazy val asyncHttpClient = new AsyncHttpClient(getHttpConfig)

  // Response object used when loading a file:// URL, sort of fakes an HTTP response for a fixed set of bytes
  // Probably doesn't really handle character encoding correctly, definitely doesn't emulate header fields
  private class FileResponse(val url: String, val body: String) extends AHCResponse {
    def getStatusCode(): Int = 200
    def getStatusText(): String = "OK"

    def getResponseBodyAsBytes(): Array[Byte] = body.getBytes()
    def getResponseBodyAsStream(): java.io.InputStream = new java.io.ByteArrayInputStream(body.getBytes())
    def getResponseBodyExcerpt(maxLength: Int, charst: String): String = body.take(maxLength)
    def getResponseBody(charset: String): String = body
    def getResponseBodyExcerpt(maxLength: Int) = body.take(maxLength)
    def getResponseBody(): String = body

    def getUri(): java.net.URI = new java.net.URI(url)

    // TODO Header stuff not really implemented yet. Should we make up sensible defaults?
    def getContentType(): String = "some content type"
    def getHeader(name: String): String = ""
    def getHeaders(name: String): java.util.List[String] = new java.util.LinkedList
    def getHeaders(): com.ning.http.client.FluentCaseInsensitiveStringsMap = new com.ning.http.client.FluentCaseInsensitiveStringsMap

    def isRedirected(): Boolean = false

    override def toString(): String = body // need the override as it re-implements scala standard toString

    def getCookies(): java.util.List[com.ning.http.client.Cookie] = new java.util.LinkedList

    def hasResponseStatus(): Boolean = true
    def hasResponseHeaders(): Boolean = true
    def hasResponseBody(): Boolean = true
  }

  private def makeFileResponse(url: String): Response = {
    require(url.toLowerCase.startsWith("file://"))
    val fname = url.drop(7)
    Logger.debug("Retrieving file URL " + fname)
    new FileResponse(url, fromFile(fname).mkString)
  }

  // Since AsyncHTTPClient has an executor anyway, allow re-use (if desired) for an Akka Promise execution context
  lazy val executionContext: ExecutionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())

  // Execute an asynchronous HTTP request, with given callbacks for success and failure
  def request(resource: DocumentAtURL,
    onSuccess: (Response) => Unit,
    onFailure: (Throwable) => Unit) = {

    val url = resource.textURL

    if (url.toLowerCase.startsWith("file://")) {

      // handle file: URLs for unit tests
      var optResponse: Option[Response] = None
      try {
        optResponse = Some(makeFileResponse(url))
      } catch {
        case t: Throwable => onFailure(t)
      }
      if (optResponse.isDefined)
        onSuccess(optResponse.get)

    } else {
      // An actual HTTP request, woo-hoo!

      val responseHandler = new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
          onSuccess(response)
          response
        }
        override def onThrowable(t: Throwable) = {
          onFailure(t)
        }
      }

      resource match {
        case r: DocumentAtURL with BasicAuth => getWithBasicAuth(r, responseHandler)
        case _ => asyncHttpClient.prepareGet(url).execute(responseHandler)
      }
    }
  }

  private def getWithBasicAuth(resource: DocumentAtURL with BasicAuth,
    responseHandler: AsyncCompletionHandler[Response]) = {

    val realm = new Realm.RealmBuilder()
      .setPrincipal(resource.username)
      .setPassword(resource.password)
      .setUsePreemptiveAuth(true)
      .setScheme(AuthScheme.BASIC)
      .build();

    asyncHttpClient.prepareGet(resource.textURL).setRealm(realm).execute(responseHandler)
  }

  // Version that returns a Future[Response]
  def apply(resource: DocumentAtURL): Future[Response] = {

    implicit val context = executionContext
    var promise = Promise[Response]() // uses executionContext derived from asyncClient executor

    def responseHandler = new AsyncCompletionHandler[Response]() {
      override def onCompleted(response: Response) = {
        promise.success(response)
        response
      }
      override def onThrowable(t: Throwable) = {
        promise.failure(t)
      }
    }

    asyncHttpClient.prepareGet(resource.textURL).execute(responseHandler)

    promise.future
  }

  // You probably shouldn't ever call this :)
  // it will block the thread
  def blockingHttpRequest(url: String): String = {
    val f = asyncHttpClient.prepareGet(url).execute()
    f.get().getResponseBody()
  }
}

