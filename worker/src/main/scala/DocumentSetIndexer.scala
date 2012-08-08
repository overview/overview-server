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
import akka.routing.SmallestMailboxRouter

// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])

// Case classes modeling the messages that our actors can send one another
case class GetText(doc : DCDocument)
case class GetTextSucceeded(doc : DCDocument, text : String)
case class GetTextFailed(doc : DCDocument, error:Throwable)
case class DocsToRetrieve(docs:DCSearchResult)
case class NoMoreDocsToRetrieve

// Single object that interfaces to Ning's Async HTTP library
object AsyncHttpRequest {  
  
  private lazy val asyncHttpClient = new AsyncHttpClient()
  
  // Since AsyncHTTPClient has an executor anyway, allow re-use (if desired) for an Akka Promise execution context
  lazy val executionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())
  
  // Execute an asynchronous HTPP request, with given callbacks for success and failure
  def apply(url       : String, 
            onSuccess : (Response) => Unit, 
            onFailure : (Throwable) => Unit ) = {
    
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
  
  // You probably shouldn't ever call this :)
  // it will block the process
  def BlockingHttpRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread  
  }
}

// Actor that retrieves the text for a single document, via HTTP request 
class SingleDocRetriever extends Actor {
 
    def receive = {
      case GetText(doc) => 
        //println(this.toString + ": received GetText for " + doc.resources.text)
        val requester = sender    // sender is a method, save return value for use in closures below
      AsyncHttpRequest(
         doc.resources.text,
         { result:Response => requester ! GetTextSucceeded(doc, result.getResponseBody()) },
         { t:Throwable => requester ! GetTextFailed(doc, t)})
  }
}

 class DocsetRetriever(val documentSet : DocumentSet,
                       val vectorGen : DocumentVectorGenerator,
                       finished : Promise[Int]) 
 extends Actor {
  
  var allDocsIn:Boolean = false   // have we received all documents to proces (via DocsToRetrieve messages?)
  var numPendingRetrievals = 0
  var numRetrieved = 0
  
  val nRetrievers = 4          // number of simultaneous HTTP connections to try
  val retrieverRouter = context.actorOf(
    Props[SingleDocRetriever].withRouter(SmallestMailboxRouter(nRetrievers)), name = "retrieverRouter")
  
  def receive = {     
    // When we get a message with a list of docs to retrieve, queue up a GetText message for each one
    case DocsToRetrieve(docs) =>
      //println("DocsToRetrieve")
      require(allDocsIn == false)   // can't sent DocsToRetrieve after AllDocsIn
      for (doc <- docs.documents) {           
        numPendingRetrievals += 1
        retrieverRouter ! GetText(doc)
      }

    // Client sends this message to indicate that document listing is complete. 
    case NoMoreDocsToRetrieve =>
      //println("NoMoreDocsToRetrieve")
      allDocsIn = true
      
    case GetTextSucceeded(doc, text) =>
      numPendingRetrievals -= 1
      numRetrieved += 1
      //println("Retriever: Document text retrieval succeeded for " + doc.resources.text + " length " + text.length)
      val newDoc = new Document(doc.title, doc.resources.text, doc.canonical_url)
      documentSet.addDocument(newDoc)
      newDoc.save()
      vectorGen.AddDocument(newDoc.id, Lexer.makeTerms(text))
      checkDone
      
    case GetTextFailed(doc, error) =>
      numPendingRetrievals -= 1
      println("Exception retrieving document text at " + doc.resources.text +" : " + error.toString)
      // eventually, requeue, retry?
      checkDone
  }
  
  // If there are no more documents to retrieve, and the retriever queue is empty, we are done, signal by fulfilling promise
  def checkDone = {
    if (allDocsIn && (numPendingRetrievals==0)) {
      finished.success(numRetrieved)
      context.stop(self)
    }
  }
}
    
class DocumentSetIndexer(var documentSet:DocumentSet) {

  implicit val context = ActorSystem("DocumentRetriever")
  
  // Query documentCloud and create one document object for each doc returned by the query
  def RetrieveAndIndexDocuments() : Future[DocumentSetVectors] = {
    
    val vectorsPromise = Promise[DocumentSetVectors]()
    
    // create the master
    val retrievalDone = Promise[Int]()
    val vectorGen = new DocumentVectorGenerator
    val retriever = context.actorOf( Props(new DocsetRetriever(documentSet, vectorGen, retrievalDone)), name = "retriever")
    
    // Blocking document list retrieval for the moment
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?per_page=500&q=" + documentSet.query
    val response = AsyncHttpRequest.BlockingHttpRequest(documentCloudQuery)
    val result = parse[DCSearchResult](response)              // TODO error handling, log bad result from DC here
    
    // Send DC search results to retriever actor
    retriever ! DocsToRetrieve(result)          // TODO loop over pages of DC results
    retriever ! NoMoreDocsToRetrieve

    // Now wait for it to finish
    retrievalDone onSuccess {
      case numDocsRetrieved:Int => 
        // println(numDocsRetrieved + " documents retrieved")
        vectorsPromise.success(vectorGen.DocumentVectors)
    }
    
    vectorsPromise
  }

  private def printElapsedTime(op:String, t0 : Long) {
    val t1 = System.nanoTime()
    println(op + ", time: " + (t1 - t0)/1e9 + " seconds")
  }
  
  def BuildTree() = {
    val t0 = System.nanoTime()
    val vectorsPromise = RetrieveAndIndexDocuments()
    
    vectorsPromise onSuccess {
      case docSetVecs:DocumentSetVectors => 
        printElapsedTime("Retrieved " + documentSet.documents.size + " documents", t0)

        val t1 = System.nanoTime()
        val docTree = BuildDocTree(docSetVecs)
        printElapsedTime("Indexed documents", t1)
        
        val t2 = System.nanoTime()
        documentSet.update();            
        printElapsedTime("Saved DocumentSet to DB", t2)
    }
  } 
}