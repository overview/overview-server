/**
 * DocumentCloudSource.scala
 * Given a search query, generate a list of GenericDocument objects corresponding to DocumentCloud docs
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import java.net.URLEncoder
import akka.dispatch.{ Await, Future, Promise }
import akka.util.Timeout
import com.codahale.jerkson.Json.parse
import com.ning.http.client.Response
import org.overviewproject.http.{ AsyncHttpRetriever, BasicAuth, DocumentAtURL, PrivateDocumentAtURL, NonRedirectingHttpRequest }
import overview.util.Logger

// The main DocumentCloudSource class produces a sequence of these...
class DCDocumentAtURL(val title: String, val documentCloudId: String, textURL: String) extends DocumentAtURL(textURL)
class PrivateDCDocumentAtURL(title: String, documentCloudId: String, textURL: String, val username: String, val password: String)
  extends DCDocumentAtURL(title, documentCloudId, textURL) with BasicAuth // case-to-case class inheritance is deprecated

// Define the bits of the DocumentCloud JSON response that we're interested in.
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.)
// Should really be private to DocumentCloudSource.parseResults but Jerkson gives errors, see
// https://groups.google.com/forum/?fromgroups#!topic/play-framework/MKNPYOj9LBA%5B1-25%5D
case class DCDocumentResources(text: String)
case class DCDocument(id: String, title: String, access: String, canonical_url: String, resources: DCDocumentResources)
case class DCSearchResult(total: Int, documents: Seq[DCDocument])

class DocumentCloudSource(asyncHttpRetriever: AsyncHttpRetriever,
  val query: String,
  documentCloudUserName: Option[String] = None,
  documentCloudPassword: Option[String] = None) extends Traversable[DCDocumentAtURL] {

  private val redirectingHttpRetriever = new NonRedirectingHttpRequest // TODO: combine with asyncHttpRetriever

  // --- configuration ---
  private val pageSize = 20 // number of docs to retreive on each page of DC search results
  private val maxDocuments = 50000 // cut off a document set if it's bigger than this

  // --- private ---
  private var numDocuments: Option[Int] = None
  private var numDocumentsReturned = 0

  private def pageQuery(pageNum: Int, myPageSize: Int = pageSize) = {
    val searchURL = "https://www.documentcloud.org/api/search.json?per_page=" + myPageSize +
      "&page=" + pageNum + "&q=" + URLEncoder.encode(query, "UTF-8")
    (documentCloudUserName, documentCloudPassword) match {
      case (Some(n), Some(p)) => new PrivateDocumentAtURL(searchURL, n, p)
      case _ => DocumentAtURL(searchURL)
    }

  }

  // we use a promise to sync the main call with our async callbacks, and propagate errors
  private implicit val executionContext = asyncHttpRetriever.executionContext // needed to run the promise object
  private val done = Promise[Unit]()
  
  // Parse a single page of results (from JSON to DCDearchResult), create DocumentAtURL objects, call f on them
  // Returns number of documents processed
  private def parseResults[U](pageNum: Int, pageText: String, f: DCDocumentAtURL => U): Int = {

    var numParsed = 0

    // For each returned document, package up the result in a DocumentAtURL object, and call f on it
    val result = parse[DCSearchResult](pageText)
    numDocuments = Some(scala.math.min(result.total, maxDocuments))
    Logger.debug("Got DocumentCloud results page " + pageNum + " with " + result.documents.size + " docs.")

    // For private documents, we have to resolve the redirect through DocumentCloud. Do this async
    var redirects = Seq[Future[DCDocumentAtURL]]()

    for (doc <- result.documents) {

      // capped returned documents at maxDocuments
      if (numDocumentsReturned < maxDocuments) {
        val dcDocumentURL = "https://www.documentcloud.org/api/documents/" + doc.id + ".txt"

        if (doc.access == "public")
          f(new DCDocumentAtURL(doc.title, doc.id, dcDocumentURL)) // public doc, just call f immediately
        else {
          val privateQuery = (documentCloudUserName, documentCloudPassword) match {
            case (Some(n), Some(p)) => new PrivateDCDocumentAtURL(doc.title, doc.id, dcDocumentURL, n, p)
            case _ => throw new Exception("Can't access private documents without credentials")
          }
          f(privateQuery)
        }

        numDocumentsReturned += 1
        numParsed += 1
      }
    }

    // Wait for all our redirect calls to complete, then call f on each
    val urlsFuture = Future.sequence(redirects) // waits for all PrivateDocURL to complete, also rethrows exceptions
    val urls = Await.result(urlsFuture, Timeout.never.duration)
    urls.map(f)

    numParsed
  }

  // Retrieve each page of document results asynchronously, calling ourself recursively (weird, but...)
  private def getNextPage[U](pageNum: Int, f: DCDocumentAtURL => U): Unit = {

    Logger.debug("Retrieving DocumentCloud results for query " + query + ", page " + pageNum)
    asyncHttpRetriever.request(pageQuery(pageNum),

      { response: Response =>

        var numParsed = 0
        try {
          numParsed = parseResults(pageNum, response.getResponseBody(), f)
        } catch {
          case t: Throwable =>
            Logger.error("Exception parsing DocumentCloud query results: " + t)
            done.failure(t)
        }

        if (numParsed == pageSize)
          getNextPage(pageNum + 1, f) // we got a full page, get the next via recursive call
        else
          done.success() // non-full page (either no more docs or we've hit out max), we're done
      },

      { t: Throwable =>
        Logger.error("Exception retrieving DocumentCloud query results: " + t)
        done.failure(t)
      })
  }

  // --- public ---
  def foreach[U](f: DCDocumentAtURL => U): Unit = {
    getNextPage(1, f) // start at first page
    Await.result(done, Timeout.never.duration) // wait here until all pages retrieved (will also rethrow any exceptions generated)
  }

  // size is defined on Traversable, but we redefine here for efficiency (otherwise we must retrieve all results)
  // Get it from from JSON, only once, lazily
  override def size = {
    if (numDocuments.isEmpty) {
      Logger.debug("Extra document page retrieval caused by DocumentCloudSource.size invocation")
      val pageText = asyncHttpRetriever.blockingHttpRequest(pageQuery(1, 1).textURL) // grab one document from first page. blocks thread to do it.
      val result = parse[DCSearchResult](pageText)
      numDocuments = Some(scala.math.min(result.total, maxDocuments))
    }
    numDocuments.get
  }

}
