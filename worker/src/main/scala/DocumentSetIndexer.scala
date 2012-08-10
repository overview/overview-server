package clustering 

import models._
import ClusterTypes._

import scala.collection.JavaConversions._
import scala.collection.mutable
import com.codahale.jerkson.Json._
import com.ning.http.client._
import akka.dispatch.{ExecutionContext,Future,Promise}
import akka.actor._
import akka.pattern.ask
import akka.routing.SmallestMailboxRouter

// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])

// Single object that interfaces to Ning's Async HTTP library
object AsyncHttpRequest {  
  
  private def getHttpConfig = {
    val builder = new AsyncHttpClientConfig.Builder()
    val config = builder
        .setFollowRedirects(true)
        .setCompressionEnabled(true)
        .setAllowPoolingConnection(true)
        .build
   config
  }
      
  private lazy val asyncHttpClient = new AsyncHttpClient(getHttpConfig)
  
  // Since AsyncHTTPClient has an executor anyway, allow re-use (if desired) for an Akka Promise execution context
  lazy val executionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())
  
  // Execute an asynchronous HTTP request, with given callbacks for success and failure
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
  
  // Version that returns a Future[Response]
  def apply(url:String) : Future[Response] = {  
    
    implicit val context = executionContext
    var promise = Promise[Response]()         // uses executionContext derived from asyncClient executor
    
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
          promise.success(response)
          response
        }
        override def onThrowable(t: Throwable) = {
          promise.failure(t)
        }
      })
      
    promise
  }
  
  // You probably shouldn't ever call this :)
  // it will block the thread
  def BlockingHttpRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      f.get().getResponseBody()
  }
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
        //println(this.toString + ": received GetText for " + doc.resources.text)
        val requester = sender    // sender is a method, save return value for use in closures below
      AsyncHttpRequest(
         doc.resources.text,
         { result:Response => requester ! GetTextSucceeded(doc, result.getResponseBody()) },
         { t:Throwable => requester ! GetTextFailed(doc, t)})
  }
}

case class DocRetrievalError(doc:DCDocument, error:Throwable)

class DocsetRetriever(val documentSet : DocumentSet,
                      val vectorGen : DocumentVectorGenerator,
                      finished : Promise[Seq[DocRetrievalError]]) 
 extends Actor {
  
  var allDocsIn:Boolean = false   // have we received all documents to proces (via DocsToRetrieve messages?)
  val maxInFlight = 8             // number of simultaneous HTTP connections to try
  var httpReqInFlight = 0
  var numRetrieved = 0
  
  var requestQueue = mutable.Queue[DCDocument]()
  var errorQueue = mutable.Queue[DocRetrievalError]()

  // This initiates more HTTP requests up to maxInFlight. When each request completes or fails, we get a message
  // We also check here to see if we are all done, in which case we set the promise
  def spoolRequests {
    while (!requestQueue.isEmpty && httpReqInFlight < maxInFlight) {
      val doc = requestQueue.dequeue
      AsyncHttpRequest(doc.resources.text, 
          { result:Response => self ! GetTextSucceeded(doc, result.getResponseBody) },
          { t:Throwable => self ! GetTextFailed(doc, t) } )
      httpReqInFlight += 1
    } 
    
    if (allDocsIn && httpReqInFlight==0 && requestQueue.isEmpty) {
      finished.success(errorQueue)
      context.stop(self)
    }
  }
  
  def receive = {     
    // When we get a message with a list of docs to retrieve, queue up a GetText message for each one
    case DocsToRetrieve(docs) =>
      //println("DocsToRetrieve")
      require(allDocsIn == false)   // can't sent DocsToRetrieve after AllDocsIn
      requestQueue ++= docs.documents
      spoolRequests

    // Client sends this message to indicate that document listing is complete. 
    case NoMoreDocsToRetrieve =>
      //println("NoMoreDocsToRetrieve")
      allDocsIn = true
      
    case GetTextSucceeded(doc, text) =>
      httpReqInFlight -= 1
      numRetrieved += 1
      println("WORKER: Retrieved document " + numRetrieved + " from " + doc.resources.text)
      val newDoc = new Document(doc.title, doc.resources.text, doc.canonical_url)
      documentSet.addDocument(newDoc)
      newDoc.save()
      vectorGen.addDocument(newDoc.id, Lexer.makeTerms(text))
      spoolRequests
      
    case GetTextFailed(doc, error) =>
      httpReqInFlight -= 1
      println("WORKER: Exception retrieving document from " + doc.resources.text +" : " + error.toString)
      errorQueue += DocRetrievalError(doc,error)
      spoolRequests
  }
}

class DocumentSetIndexer(var documentSet:DocumentSet) {

  // number of documents to retrieve on each page of search results
  // Too large, and we wait a long time to start retrieving the actual text. Too small, and we have needless HTTP traffic 
  private val pageSize = 500
  
  private def printElapsedTime(op:String, t0 : Long) {
    val t1 = System.nanoTime()
    println(op + ", time: " + (t1 - t0)/1e9 + " seconds")
  }
  
  // Query documentCloud and create one document object for each doc returned by the query
  def RetrieveAndIndexDocuments() : Future[DocumentSetVectors] = {     
    
    // create the actor
    implicit val context = ActorSystem("DocumentRetriever")
    val retrievalDone = Promise[Seq[DocRetrievalError]]()
    val vectorGen = new DocumentVectorGenerator
    val retriever = context.actorOf( Props(new DocsetRetriever(documentSet, vectorGen, retrievalDone)), name = "retriever")
        
    // This is what we will ultimately return
    val vectorsPromise = Promise[DocumentSetVectors]()

    // Retrieve each page of document results, calling AsyncHttpRequest recursively (weird, but...)
    // Send the results to DocsetRetriever actor as we get them
    // If any of the DC calls to list the document set fail, the whole thing fails
    def getDocuments(pageNum:Int) : Unit = {
      val documentCloudQuery = "http://www.documentcloud.org/api/search.json?per_page=" + pageSize + "&page=" + pageNum + "&q=" + documentSet.query
      AsyncHttpRequest(documentCloudQuery, 
          { response:Response => 
            
              val result = parse[DCSearchResult](response.getResponseBody())

              // send the docs we get back to the retriever for queing
              if (!result.documents.isEmpty) {
                println("WORKER: Got document set result page " + pageNum + " with " + result.documents.size + " docs.")
                retriever ! DocsToRetrieve(result)
              }

              // if we got a full page back, try the next page, otherwise done
              if (result.documents.size == pageSize) {
                  getDocuments(pageNum+1)   
              } else {
                retriever ! NoMoreDocsToRetrieve
              }
          },
          { error:Throwable => 
            retriever ! PoisonPill
            vectorsPromise.failure(error) 
          })
    }
    getDocuments(1) // start at this page
    
    // When the docsetRetriever finishes, compute vectors and complete the promise
    retrievalDone onComplete {
      case Left(error) => 
        println("WORKER: document set retrieval error: " + error)
      case Right(docsNotFetched) => 
        println("WORKER: document set retrieval succeded, with " + docsNotFetched.length + " not fetched")
        vectorsPromise.success(vectorGen.documentVectors)
    }
    
    vectorsPromise
  }

  
  def BuildTree() = {
    val t0 = System.nanoTime()
    val vectorsPromise = RetrieveAndIndexDocuments()
    
    vectorsPromise onSuccess {
      case docSetVecs:DocumentSetVectors => 
        printElapsedTime("WORKER: Retrieved and indexed " + documentSet.documents.size + " documents", t0)

        val t1 = System.nanoTime()
        val docTree = BuildDocTree(docSetVecs)
        printElapsedTime("WORKER: Clustered documents", t1)
        //println(docTree.prettyString)
        
        val t2 = System.nanoTime()
        documentSet.update();            
        printElapsedTime("WORKER: Saved DocumentSet to DB", t2)
    }
  } 
}