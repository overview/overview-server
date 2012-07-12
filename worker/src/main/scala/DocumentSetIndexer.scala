package clustering 

import models._
import scala.collection.JavaConversions._
import com.ning.http.client.AsyncHttpClient
import com.codahale.jerkson.Json._

// Define the bits of the DocumentCloud JSON response that we're interested in. 
// This omits many returned fields, but that's good for robustness (don't demand what we don't use.) 
case class DCDocumentResources(text:String)
case class DCDocument(title: String, canonical_url:String, resources:clustering.DCDocumentResources)
case class DCSearchResult(documents: Seq[clustering.DCDocument])


class DocumentSetIndexer(var documentSet:DocumentSet) {

  // You probbly shouldn't ever call this :)
  // it's a shim until I write better (async) handling, at the moment it will block the worker process
  def BlockingHTTPRequest(url:String) : String = {
      val asyncHttpClient = new AsyncHttpClient()
      val f = asyncHttpClient.prepareGet(url).execute()
      val response = f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread
      asyncHttpClient.close()
      
      return response
  }

  // Query documentCloud and create one document object for each doc returned by the query
  def createDocuments() = {
        
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
  
    documentSet.update();     
  }
  
  
  // Retrieve document text and produce TF-IDF vector space representation
  def generateTFIDF() : DocumentVectorGenerator#DocumentSetVectors = {

    val vectorGen = new DocumentVectorGenerator
    
    for (document <- documentSet.documents) {
     
      val text = BlockingHTTPRequest(document.textUrl)
      val terms = Lexer.make_terms(text)
      vectorGen.addDocument(document.id, terms)
    }
    
    return vectorGen.getVectors()
  }
  
  // Index a collection of documents, TF-IDF style
  def indexDocuments() = {

    val docSetVecs = generateTFIDF()
    
    for ((docid, tfidf) <- docSetVecs) {
      println("---------------------------")
      println(tfidf.toList.sortBy(-_._2))  // sort by decreasing weight      
    }
 
    println("---------------------------")
    println("Indexed " + documentSet.documents.size + " documents.")
  }
  
}