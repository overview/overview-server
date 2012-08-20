/**
 * BulkHttpRetriever.scala
 * Retrieve a list of URLs, call back with the text of each
 * 
 * Overview Project, created August 2012
 * 
 * @author Jonathan Stray
 * 
 */

package overview.http 

import overview.logging._

import scala.collection.mutable
import akka.dispatch.{ExecutionContext,Future,Promise}
import akka.actor._
import akka.util.Timeout


// Input and output types...
class DocumentAtURL(val textURL:String)
case class DocRetrievalError(doc:DocumentAtURL, error:Throwable)


object BulkHttpRetriever {
  
  // Case classes modeling the messages that our actors can send one another
  private case class GetText(doc : DocumentAtURL)
  private case class GetTextSucceeded(doc : DocumentAtURL, text : String, startTime:Long)
  private case class GetTextFailed(doc : DocumentAtURL, error:Throwable)
  private case class DocToRetrieve(doc:DocumentAtURL)
  private case class NoMoreDocsToRetrieve()
  
  private class BulkHttpActor[T <: DocumentAtURL](writeDocument : (T,String) => Unit, 
                                                 finished : Promise[Seq[DocRetrievalError]] ) 
   extends Actor {
    
    var allDocsIn:Boolean = false   // have we received all documents to proces (via DocsToRetrieve messages?)
    val maxInFlight = 4             // number of simultaneous HTTP connections to try
    var httpReqInFlight = 0
    var numRetrieved = 0
    
    var requestQueue = mutable.Queue[DocumentAtURL]()
    var errorQueue = mutable.Queue[DocRetrievalError]()
  
    // This initiates more HTTP requests up to maxInFlight. When each request completes or fails, we get a message
    // We also check here to see if we are all done, in which case we set the promise
    def spoolRequests {
      while (!requestQueue.isEmpty && httpReqInFlight < maxInFlight) {
        val doc = requestQueue.dequeue
        val startTime = System.nanoTime
        AsyncHttpRequest(doc.textURL, 
            { result:AsyncHttpRequest.Response => self ! GetTextSucceeded(doc, result.getResponseBody, startTime) },
            { t:Throwable => self ! GetTextFailed(doc, t) } )
        httpReqInFlight += 1
      } 
      
      if (allDocsIn && httpReqInFlight==0 && requestQueue.isEmpty) {
        finished.success(errorQueue)
        context.stop(self)
      }
    }
    
    def receive = {     
      // When we get a message with a doc to retrieve, queue it up
      case DocToRetrieve(doc) =>
        require(allDocsIn == false)   // can't send DocsToRetrieve after AllDocsIn
        requestQueue += doc
        spoolRequests
  
      // Client sends this message to indicate that document listing is complete. 
      case NoMoreDocsToRetrieve =>
        allDocsIn = true
        spoolRequests     // needed to stop us, in boundary case when DocsToRetrieve was never sent to us 
        
      case GetTextSucceeded(doc, text, startTime) =>
        httpReqInFlight -= 1
        numRetrieved += 1
        val elapsedSeconds = (System.nanoTime - startTime)/1e9
        Logger.debug("Retrieved document " + numRetrieved + 
               ", from: " + doc.textURL + 
               ", size: " + text.size + 
               ", time: " + ("%.2f" format elapsedSeconds) + 
               ", speed: " + ((text.size/1024) / elapsedSeconds + 0.5).toInt + " KB/s")
        writeDocument(doc.asInstanceOf[T], text)       
        spoolRequests
        
      case GetTextFailed(doc, error) =>
        httpReqInFlight -= 1
        Logger.warn("Exception retrieving document from " + doc.textURL +" : " + error.toString)
        errorQueue += DocRetrievalError(doc,error)
        spoolRequests
    }
  }

  
  def apply[T <: DocumentAtURL](sourceDocList : Traversable[T],
                                writeDocument : (T,String) => Unit ) : Promise[Seq[DocRetrievalError]] = {     
    
    Logger.info("Beginning HTTP document set retrieval")

    // create the actor
    implicit val context = ActorSystem("DocumentRetriever")
    val retrievalDone = Promise[Seq[DocRetrievalError]]
    val retriever = context.actorOf( Props(new BulkHttpActor(writeDocument, retrievalDone)), name = "retriever")
        
    // Feed a sequence of DocumentAtURL objects to retriever
    for (doc <- sourceDocList) {
      retriever ! DocToRetrieve(doc)
    }
    retriever ! NoMoreDocsToRetrieve

    // return promise (of list of errors retrieving documents)
    retrievalDone
  }
}