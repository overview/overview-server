/**
 * BulkHttpRetriever.scala
 * Retrieve a list of URLs, call back with the text of each
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.http

import overview.util.{ Logger, WorkerActorSystem }
import scala.collection.mutable
import akka.dispatch.{ ExecutionContext, Future, Promise }
import akka.actor._
import akka.util.Timeout
import com.ning.http.client.Response


// Input and output types...
case class DocumentAtURL(val textURL: String)
class PrivateDocumentAtURL(val textUrl: String, val username: String, val password: String)
  extends DocumentAtURL(textUrl) with BasicAuth // case-to-case class inheritance is deprecated
case class DocRetrievalError(doc: DocumentAtURL, error: Throwable)

class BulkHttpRetriever[T <: DocumentAtURL](asyncHttpRetriever: AsyncHttpRetriever) {

  // Case classes modeling the messages that our actors can send one another
  private case class GetText(doc: DocumentAtURL)
  private case class GetTextSucceeded(doc: DocumentAtURL, text: String, startTime: Long)
  private case class GetTextFailed(doc: DocumentAtURL, error: Throwable)
  private case class DocToRetrieve(doc: DocumentAtURL)
  private case class NoMoreDocsToRetrieve()

  protected class BulkHttpActor[T <: DocumentAtURL](writeDocument: (T, String) => Boolean,
    finished: Promise[Seq[DocRetrievalError]])
    extends Actor {

    protected case class Request(doc: DocumentAtURL, handler: (DocumentAtURL, Long, Response) => Unit)
    
    protected var allDocsIn: Boolean = false // have we received all documents to process (via DocsToRetrieve messages?)
    protected val maxInFlight = 4 // number of simultaneous HTTP connections to try
    protected var httpReqInFlight = 0
    protected var numRetrieved = 0

    protected var requestQueue = mutable.Queue[Request]()
    protected var errorQueue = mutable.Queue[DocRetrievalError]()

    protected var cancelJob: Boolean = false
    
    // This initiates more HTTP requests up to maxInFlight. When each request completes or fails, we get a message
    // We also check here to see if we are all done, in which case we set the promise
    def spoolRequests {
      if (requestQueue.isEmpty && httpReqInFlight == 0)
        Logger.debug("BulkHttpRetriever idle: request queue is empty, no documents in flight.")
        
      while (!cancelJob && !requestQueue.isEmpty && httpReqInFlight < maxInFlight) {
        val request = requestQueue.dequeue
        val doc = request.doc

        val startTime = System.nanoTime
        asyncHttpRetriever.request(doc, request.handler(doc, startTime, _), requestFailed(doc, _))

        httpReqInFlight += 1
      }

      if (cancelJob || (allDocsIn && httpReqInFlight == 0 && requestQueue.isEmpty)) {
        finished.success(errorQueue)
        context.stop(self)
      }
    }

    def receive = {
      // When we get a message with a doc to retrieve, queue it up
      case DocToRetrieve(doc) =>
        require(allDocsIn == false) // can't send DocsToRetrieve after AllDocsIn
        requestQueue += Request(doc, requestSucceeded)
        spoolRequests

      // Client sends this message to indicate that document listing is complete.
      case NoMoreDocsToRetrieve =>
        allDocsIn = true
        spoolRequests // needed to stop us, in boundary case when DocsToRetrieve was never sent to us

      case GetTextSucceeded(doc, text, startTime) =>
        httpReqInFlight -= 1
        numRetrieved += 1
        val elapsedSeconds = (System.nanoTime - startTime) / 1e9
        Logger.debug("Retrieved document " + numRetrieved +
          ", from: " + doc.textURL +
          ", size: " + text.size +
          ", time: " + ("%.2f" format elapsedSeconds) +
          ", speed: " + ((text.size / 1024) / elapsedSeconds + 0.5).toInt + " KB/s")
        try {
          if (!writeDocument(doc.asInstanceOf[T], text)) cancelJob = true
        } catch {
          case e => {
            Logger.error("Unable to process " + doc.textURL + ":" + e.getMessage)
            finished.failure(e)
            context.stop(self)
          }
        }

        spoolRequests

      case GetTextFailed(doc, error) =>
        httpReqInFlight -= 1
        Logger.warn("Exception retrieving document from " + doc.textURL + " : " + error.toString)
        errorQueue += DocRetrievalError(doc, error)
        spoolRequests
    }
    
    protected def requestSucceeded(doc: DocumentAtURL, startTime: Long, result: Response) = 
      self ! GetTextSucceeded(doc, result.getResponseBody, startTime)

    protected def requestFailed(doc: DocumentAtURL, t: Throwable) =  self ! GetTextFailed(doc, t) 
  }

  protected def retrieveDocument(retriever: ActorRef, doc: T): Unit =  retriever ! DocToRetrieve(doc)

  def retrieve(sourceDocList: Traversable[T], writeDocument: (T, String) => Boolean)
  (implicit context: ActorSystem): Promise[Seq[DocRetrievalError]] = {

    Logger.info("Beginning HTTP document set retrieval")

    val retrievalDone = Promise[Seq[DocRetrievalError]]
    val retriever = context.actorOf(Props(new BulkHttpActor(writeDocument, retrievalDone)), name = "retriever")

    // Feed a sequence of DocumentAtURL objects to retriever
    for (doc <- sourceDocList) {
      retrieveDocument(retriever, doc)      
    }
    retriever ! NoMoreDocsToRetrieve

    // return promise (of list of errors retrieving documents)
    retrievalDone

  }
}
