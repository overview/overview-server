package clustering 

import models._
import ClusterTypes._
import overview.http._
import overview.logging._
import persistence.{DocumentWriter, NodeWriter}
import database.DB

//import scala.collection.JavaConversions._
import scala.collection.mutable
import akka.dispatch.{ExecutionContext,Future,Promise}
import akka.actor._


class DocumentAtURL(val title:String, 
                    val viewURL:String, 
                    val textURL:String)
{
  var text : Option[String] = None      // empty until loaded, by someone else
}

// Case classes modeling the messages that our actors can send one another
case class GetText(doc : DocumentAtURL)
case class GetTextSucceeded(doc : DocumentAtURL, text : String, startTime:Long)
case class GetTextFailed(doc : DocumentAtURL, error:Throwable)
case class DocToRetrieve(doc:DocumentAtURL)
case class NoMoreDocsToRetrieve()

case class DocRetrievalError(doc:DocumentAtURL, error:Throwable)

class DocsetRetriever(val documentWriter : DocumentWriter,
                      val vectorGen : DocumentVectorGenerator,
                      finished : Promise[Seq[DocRetrievalError]]) 
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
             
      val documentId = DB.withConnection { implicit connection =>
      	  documentWriter.write(doc.title, doc.textURL, doc.viewURL)
      }
      
      vectorGen.addDocument(documentId, Lexer.makeTerms(text))      	  
      spoolRequests
      
    case GetTextFailed(doc, error) =>
      httpReqInFlight -= 1
      Logger.warn("Exception retrieving document from " + doc.textURL +" : " + error.toString)
      errorQueue += DocRetrievalError(doc,error)
      spoolRequests
  }
}

class DocumentSetIndexer(query: String, 
						 nodeWriter: NodeWriter, documentWriter: DocumentWriter) {

  
  private def printElapsedTime(op:String, t0 : Long) {
    val t1 = System.nanoTime()
    Logger.info(op + ", time: " + ("%.2f" format (t1 - t0)/1e9) + " seconds")
  }
  
  // Query documentCloud and create one document object for each doc returned by the query
  def RetrieveAndIndexDocuments() : Future[DocumentSetVectors] = {     
    
    Logger.info("Beginning document set retrieval")

    // create the actor
    implicit val context = ActorSystem("DocumentRetriever")
    val retrievalDone = Promise[Seq[DocRetrievalError]]()
    val vectorGen = new DocumentVectorGenerator
    val retriever = context.actorOf( Props(new DocsetRetriever(documentWriter, vectorGen, retrievalDone)), name = "retriever")
        
    // This is what we will ultimately return
    val vectorsPromise = Promise[DocumentSetVectors]()

    // Feed a sequence of DocumentAtURL objects to retriever
    val sourceDocList = new DocumentCloudSource(query)
    for (doc <- sourceDocList) {
      retriever ! DocToRetrieve(doc)
    }
    retriever ! NoMoreDocsToRetrieve

    
    // When the docsetRetriever finishes, compute vectors and complete the promise
    retrievalDone onComplete {
      case Left(error) => 
        Logger.warn("Document set retrieval error: " + error)
      case Right(docsNotFetched) => 
        Logger.info("Document set retrieval succeded, with " + docsNotFetched.length + " not fetched")
        vectorsPromise.success(vectorGen.documentVectors)
    }
    
    vectorsPromise
  }

  def BuildTree() = {
    val t0 = System.nanoTime()
    val vectorsPromise = RetrieveAndIndexDocuments()
    
    vectorsPromise onSuccess {
      case docSetVecs:DocumentSetVectors => 
        printElapsedTime("Retrieved and indexed " + docSetVecs.size + " documents", t0)

        val t1 = System.nanoTime()
        val docTree = BuildDocTree(docSetVecs)
        printElapsedTime("Clustered documents", t1)
        
        val t2 = System.nanoTime()

        DB.withTransaction { implicit connection =>
         nodeWriter.write(docTree)
        }
        
        printElapsedTime("Saved DocumentSet to DB", t2)
    }
  } 
}