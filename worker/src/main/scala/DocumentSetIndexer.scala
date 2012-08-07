package clustering 

import models._
import ClusterTypes._

import scala.collection.JavaConversions._
import com.codahale.jerkson.Json._

//import java.util.concurrent.TimeUnit

import com.ning.http.client._

import akka.actor._
import akka.dispatch.{ExecutionContext,Future,Promise}
import akka.pattern.ask
import akka.routing.RoundRobinRouter

// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])


class DocumentSetIndexer(var documentSet:DocumentSet) {

  private val asyncHttpClient = new AsyncHttpClient()
  implicit val retrievalContext = ActorSystem("DocRetrieval")    // this is the context we will use for all our async work

  // You probbly shouldn't ever call this :)
  // it's a shim until I write better (async) handling, at the moment it will block the worker process
  def BlockingHTTPRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      val response = f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread
      
      return response
  }

  // Execute an asynchronous HTPP request, with given callbacks for success and failure
  def AsyncHTTPRequest(url:String, onSuccess : (Response) => Unit, onFailure : (Throwable) => Unit) = {
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
         onSuccess(response)
         response
        }
        override def onThrowable(t: Throwable) = {
          onFailure(t)
        }
      }) 
  }
  
  // Case classes modeling the messages that our actors can send one another
  case class GetText(doc : DCDocument)
  case class GetTextSucceeded(doc : DCDocument, text : String)
  case class GetTextFailed(doc : DCDocument, error:Throwable)
  case class DocsToRetrieve(docs:DCSearchResult)
  case class NoMoreDocsToRetrieve
  
  // Actor that retrieves the text for a single document, via HTTP request 
  class SingleDocRetriever extends Actor {
 
    def receive = {
      case GetText(doc) => 
        println("GetText for " + doc.resources.text)
        AsyncHTTPRequest(
           doc.resources.text,
           { result:Response => sender ! GetTextSucceeded(doc, result.getResponseBody()) },
           { t:Throwable => sender ! GetTextFailed(doc, t)})
    }
  }
  
  class DocsetRetriever(val vectorGen: DocumentVectorGenerator, finished:Promise[Int]) extends Actor {
    
    var allDocsIn:Boolean = false   // have we received all documents to proces (via DocsToRetrieve messages?)
    var numPendingRetrievals = 0
    var numRetrieved = 0
    
    val nrOfRetrievers = 4          // number of simultaneous HTTP connections to try
    val retrieverRouter = context.actorOf(
      Props[SingleDocRetriever].withRouter(RoundRobinRouter(nrOfRetrievers)), name = "retrieverRouter")
    
    def receive = {     
      // When we get a message with a list of docs to retrieve, queue up a GetText message for each one
      case DocsToRetrieve(docs) =>
        println("DocsToRetrieve")
        require(allDocsIn == false)   // can't sent DocsToRetrieve after AllDocsIn
        for (doc <- docs.documents) {           
          retrieverRouter ! GetText(doc)
          numPendingRetrievals += 1
        }

      // Client sends this message to indicate that document listing is complete. 
      case NoMoreDocsToRetrieve =>
        println("NoMoreDocsToRetrieve")
        allDocsIn = true
        
      case GetTextSucceeded(doc, text) =>
        numPendingRetrievals -= 1
        numRetrieved += 1
        println("Document text retrieval succeeded for " + doc.resources.text)
        val newDoc = new Document(doc.title, doc.resources.text, doc.canonical_url)
        documentSet.addDocument(newDoc)
        vectorGen.AddDocument(newDoc.id, Lexer.makeTerms(text))
        newDoc.save()
        checkDone
        
      case GetTextFailed(doc, error) =>
        numPendingRetrievals -= 1
        println("Exception retrieving document text at " + doc.resources.text +" : " + error.toString)
        // eventually, requeue, retry?
        checkDone
    }
    
    // If there are no more documents to retrieve, and the retriever queue is empty, we are done, signal by fulfilling promise
    def checkDone = {
      if (allDocsIn && numPendingRetrievals==0)
        finished.success(numRetrieved)
        context.stop(self)
    }
  }
  
  
  // Query documentCloud and create one document object for each doc returned by the query
  def RetrieveAndIndexDocuments() : Future[DocumentSetVectors] = {
    
    val vectorsPromise = Promise[DocumentSetVectors]()
    
    // create the master
    val retrievalDone = Promise[Int]()
    val vectorGen = new DocumentVectorGenerator
    val retriever = retrievalContext .actorOf( Props(new DocsetRetriever(vectorGen, retrievalDone)), name = "retriever")
    
    // Blocking document list retrieval for the moment
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?q=" + documentSet.query
    val response = BlockingHTTPRequest(documentCloudQuery)
    val result = parse[DCSearchResult](response)              // TODO error handling, log bad result from DC here
    
    // Send DC search results to retriever actor
    retriever ! DocsToRetrieve(result)          // TODO loop over pages of DC results
    retriever ! NoMoreDocsToRetrieve

    // Now wait for it to finish
    retrievalDone onSuccess {
      case numDocsRetrieved:Int => 
        println(numDocsRetrieved + "documents retrieved")
        vectorsPromise.success(vectorGen.DocumentVectors)
    }
    
    vectorsPromise
  }
  

  def BuildTree() = {
    val vectorsPromise = RetrieveAndIndexDocuments()
    
    vectorsPromise onSuccess {
      case docSetVecs:DocumentSetVectors => 
        val docTree = BuildDocTree(docSetVecs)
        
        println("---------------------------")
        println("Indexed " + documentSet.documents.size + " documents.")
        println(docTree.prettyString)    
        
        documentSet.update();            
    }
  } 
  
}