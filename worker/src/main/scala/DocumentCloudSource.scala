/**
 * DocumentCloudSource.scala
 * Given a search query, generate a list of GenericDocument objects corresponding to DocumentCloud docs
 * 
 * Overview Project, created August 2012
 * 
 * @author Jonathan Stray
 * 
 */

package clustering

import overview.http.AsyncHttpRequest
import overview.http.AsyncHttpRequest.Response
import overview.logging._

import akka.dispatch.{Future,Promise,Await}
import akka.util.Timeout
import akka.util.duration._

import com.codahale.jerkson.Json._
import scala.concurrent._


// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.)
// Should really be private to DocumentCloudSource.parseResults but Jerkson gives errors, see https://groups.google.com/forum/?fromgroups#!topic/play-framework/MKNPYOj9LBA%5B1-25%5D
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:DCDocumentResources)
case class DCSearchResult(documents: Seq[DCDocument])


class DocumentCloudSource(val query:String) extends Traversable[DocumentAtURL] {

  // --- private ---
  private val pageSize = 100
  
  private def pageQuery(pageNum:Int) = {
    "http://www.documentcloud.org/api/search.json?per_page=" + pageSize + "&page=" + pageNum + "&q=" + query
  }

  // we use a promise to sync the main call with our async callbacks, and propagate errors
  private implicit val executionContext = AsyncHttpRequest.executionContext   // needed to run the promise obejct
  private val done = Promise[Unit]() 

  
  // Parse a single page of results (from JSON to DCDearchResult), create DocumentAtURL objects, call f on them
  // Returns number of documents processed
  private def parseResults[U](pageNum:Int, pageText:String, f: DocumentAtURL => U) : Int = {

    // For each returned document, package up the result in a DocumentAtURL object, and call f on it
    val result = parse[DCSearchResult](pageText)
    Logger.debug("Got DocumentCloud results page " + pageNum + " with " + result.documents.size + " docs.")

    for (doc <- result.documents) {
      f(new DocumentAtURL(doc.title, doc.canonical_url, doc.resources.text))
    }
    return result.documents.size
  }
  
  
  // Retrieve each page of document results asynchronously, calling ourself recursively (weird, but...)
  private def getNextPage[U](pageNum:Int, f : DocumentAtURL=>U) : Unit = {
    
    Logger.debug("Retrieving DocumentCloud results for query " + query + ", page " + pageNum)
    AsyncHttpRequest(pageQuery(pageNum), 
        
        { response:Response => 

            var numParsed = 0
            try {
              numParsed = parseResults(pageNum, response.getResponseBody(), f)
            } catch { 
              case t:Throwable => 
                Logger.error("Exception parsing DocumentCloud query results: " + t)
                done.failure(t) // error parsing or calling f, propagate out to main call
            }

            if (numParsed == pageSize)
              getNextPage(pageNum+1,f)      // we got a full page, get the next via recursive call
            else 
              done.success()                // non-full page, we're done
        },
        
        { t:Throwable => 
          Logger.error("Exception retrieving DocumentCloud query results: " + t)
          done.failure(t) }             // error from HTTP request, propagate error out to main call
     )
  }
  
  
  // --- public ---
  def foreach[U](f: DocumentAtURL => U) : Unit = {
    getNextPage(1,f)                           // start at first page    
    Await.result(done, Timeout.never.duration)  // wait here until all pages retrieved (will also rethrow any exceptions generated)
  }
}