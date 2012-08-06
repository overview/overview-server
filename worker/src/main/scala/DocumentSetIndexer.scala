package clustering 

import models._
import ClusterTypes._

import scala.collection.JavaConversions._
import com.codahale.jerkson.Json._

import java.util.concurrent.TimeUnit

import com.ning.http.client._


import akka.dispatch.ExecutionContext
import akka.dispatch.Future
import akka.dispatch.Promise


// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])


class DocumentSetIndexer(var documentSet:DocumentSet) {

  private val asyncHttpClient = new AsyncHttpClient()

  // You probbly shouldn't ever call this :)
  // it's a shim until I write better (async) handling, at the moment it will block the worker process
  def BlockingHTTPRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      val response = f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread
      
      return response
  }

  // Async version: return Future[Response]
  // A bit of awkwardness here to convert from Java asyncs to Akka, otherwise no magic
  def AsyncHTTPRequest(url:String) : Future[Response] = {  
    // implicits for Promise construction 
    implicit val executorService = asyncHttpClient.getConfig().executorService()
    implicit val context = ExecutionContext.fromExecutor(executorService)
    
    var promise = Promise[Response]()
    
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

  // Query documentCloud and create one document object for each doc returned by the query
  def CreateDocumentsBlocking() = {
        
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?q=" + documentSet.query
    val response = BlockingHTTPRequest(documentCloudQuery)
    val result = parse[DCSearchResult](response)              // TODO error handling, log bad result from DC here
    
    // Iterate over returned docs, each one described by a block of JSON
    // NB document.resources.text is the URL to the text, not the text itself (which we do not store)
    for (document <- result.documents) {                       
      val newDoc = new Document(document.title, document.resources.text, document.canonical_url)
      documentSet.addDocument(newDoc)
      newDoc.save()
    }
  }
 
  
  def AddDocumentsFromDCJson(response :String) {
     val result = parse[DCSearchResult](response)              // TODO error handling, log bad result from DC here
    
     println("Got them docs!")
     
    // Iterate over returned docs, each one described by a block of JSON
    // NB document.resources.text is the URL to the text, not the text itself (which we do not store)
    for (document <- result.documents) {                       
      val newDoc = new Document(document.title, document.resources.text, document.canonical_url)
      documentSet.addDocument(newDoc)
      newDoc.save()
    }
  }
  
  // Query documentCloud and create one document object for each doc returned by the query
  def CreateDocuments() = {
        
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?q=" + documentSet.query
    val promise = AsyncHTTPRequest(documentCloudQuery)
    
    promise onComplete { response =>
      response match {
        case Right(result) => AddDocumentsFromDCJson(result.getResponseBody())
        case Left(exception) => println("Exception retrieving document: " + exception) 
      }
    }
  }
  
  
  // Retrieve document text and produce TF-IDF vector space representation
  def GenerateTFIDF() : DocumentSetVectors = {

    val vectorGen = new DocumentVectorGenerator
    
    for (document <- documentSet.documents) {     
      val text = BlockingHTTPRequest(document.textUrl)
      val terms = Lexer.makeTerms(text)
      vectorGen.addDocument(document.id, terms)
    }
    
  /*  
    for (document <- documentSet.documents) {
     
      val textPromise = AsyncHTTPRequest(document.textUrl)
      textPromise onComplete { response =>
        reponse match {
          case Right(response) => {
              val terms = Lexer.makeTerms(response.GetResponseBody())
              vectorGen.addDocument(document.id, terms)        
          }
          case Left(exception) => {
           println("Exception retrieving document: " + exception) 
          }
        }
      }
    }
*/    
    return vectorGen.getVectors()
  }
  

  def BuildTree() = {
    CreateDocumentsBlocking()
    documentSet.update();       
 
    val docSetVecs = GenerateTFIDF()
    val docTree = BuildDocTree(docSetVecs)
    
    println("---------------------------")
    println("Indexed " + documentSet.documents.size + " documents.")
    println(docTree.prettyString)    
  } 
}