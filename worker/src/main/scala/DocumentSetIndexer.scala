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

  // Query documentCloud and create one document object for each doc returned by the query
  def createDocuments() = {
    val queryString = documentSet.query
    val documentCloudQuery = "http://www.documentcloud.org/api/search.json?q=" + queryString
      
    val asyncHttpClient = new AsyncHttpClient()
    val f = asyncHttpClient.prepareGet(documentCloudQuery).execute()
    val response = f.get().getResponseBody()      // blocks until result comes back. needs async() so it doesn't tie up thread
    
    val result = parse[DCSearchResult](response)
    
    // Iterate over returned docs, each one described by a block of JSON
    for (document <- result.documents) { 
      
      var title = document.title      
      var canonicalUrl = document.canonical_url
      var textUrl = document.resources.text
                      
      val newDoc = new Document(title, textUrl, canonicalUrl)
      documentSet.addDocument(newDoc)
      newDoc.save()
    }
  
    documentSet.update();     
  }
  
  // Index a collection of documents, TF-IDF style
  def indexDocuments() = {
    
    val N = documentSet.documents.size
    var idf = Map[String, Float]()            // term -> number of docs containing term
    var tf = Map[Long, Map[String, Float]]()   // docuent ID -> (term -> frequency)
    
    for (document <- documentSet.documents) {

      // Get the document text
      val asyncHttpClient = new AsyncHttpClient()
      val f = asyncHttpClient.prepareGet(document.textUrl).execute()
      val text = f.get().getResponseBody()    
      //println(text)

      // Turn into tokens
      val tokens = Lexer.make_terms(text)
      if (tokens.size > 0) {
        
        // count how many times each token appears in this doc (term frequency)      
        var termcounts = Map[String, Int]()
        for (t <- tokens) {
          val prev_count = if (termcounts.contains(t)) termcounts(t) else 0
          termcounts += (t -> (prev_count + 1))
        }
        
        // divide out document length to go from term count to term frequency
        val termfreqs = termcounts.mapValues(count => count/tokens.size.toFloat) 
        tf += (document.id.toLong -> termfreqs)
         
        // for each unique term in this doc, update how many docs each term appears in (doc frequency)
        for (t <- termcounts.keys) {
          val prev_count = if (idf.contains(t)) idf(t) else 0
          idf += (t -> (prev_count + 1))
        }
      }      
    }
    
    // Now that we've read all documents, transform the document count into idf
    // Drop all terms where count < 3 or count == N
    idf = idf.filter(kv => (kv._2 > 2) && (kv._2 < N))
    idf = idf.mapValues(count => math.log10(N / count).toFloat)
    
    // Now we have to run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    for ((docid, doctf) <- tf) {

      var tfidf = Map[String,Float]()
      var length = 0f
      for ((term,termfreq) <- doctf) {
        if (idf.contains(term)) {               // term might have been eliminated in previous step
          val weight = termfreq * idf(term)
          tfidf += (term -> weight)
          length += weight*weight
        }
      }
      length = math.sqrt(length).toFloat
      
      val normalized = tfidf.mapValues(weight => weight/length)
      
      println("---------------------------")
      println(normalized.toList.sortBy(-_._2))  // sort by decreasing weight
    }
    
    println("---------------------------")
    println("Indexed " + N + " documents.")
  }
}