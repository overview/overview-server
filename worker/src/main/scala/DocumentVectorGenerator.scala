/*
 * DocumentVectorGenerator.scala
 * Takes lists of terms for each document (post-lexing), generates weighted and normalized vectors for a document set
 * Currently just simple TF-IDF
 * 
 * Overview Project
 * 
 * Created by Jonathan Stray, July 2012
 * 
 */

package overview.clustering

import scala.collection.mutable.Map
import overview.clustering.ClusterTypes._

// Basic use: call AddDocument(docID, terms) until done, then DocumentVectors() once
class DocumentVectorGenerator {


  // --- Data ---
  var numDocs = 0 
  private var termStrings = new StringTable
  private var doccount = DocumentVector() 
  private var tf = DocumentSetVectors(termStrings)
     
  // --- Methods ---
  
  // provide limited access to our string table, so if someone has one of our vectors they can look up the IDs
  def idToString(id:TermID) = termStrings.idToString(id)
  def stringToId(s:String) = termStrings.stringToId(s)
  
  // Add one document. Takes a list of terms, which are pre-lexed strings. Order of terms and docs does not matter.
  def addDocument(docId:DocumentID, terms:Seq[String]) = {
    this.synchronized {
      if (terms.size > 0) {
          
        // count how many times each token appears in this doc (term frequency)      
        var termcounts = DocumentVector()
        for (termString <- terms) {
          val term = termStrings.stringToId(termString)
          val prev_count = termcounts.getOrElse(term,0f)
          termcounts += (term -> (prev_count + 1))
        }
        
        // divide out document length to go from term count to term frequency
        termcounts.transform((key,count) => count/terms.size.toFloat) 
        tf += (docId -> termcounts)
         
        // for each unique term in this doc, update how many docs each term appears in (doc count)
        for (term <- termcounts.keys) {
          val prev_count = doccount.getOrElse(term, 0f)
          doccount += (term -> (prev_count + 1))
        }
        
        numDocs += 1
      }
    }
  }   
  
  // Return inverse document frequency map: term -> idf
  // Drop all terms where count < 3 or count == N, and take the log of document frequency in the usual IDF way
  def Idf() : DocumentVector = {
    var idf2 = doccount.filter(kv => (kv._2 > 2) && (kv._2 < numDocs))
    idf2.transform((term,count) => math.log10(numDocs / count).toFloat)    
  }
  
  // After all documents have been added (or, really, at any point) compute the total set of document vectors
  // Previously added documents will end up with different vectors as new docs are added, due to IDF term
  def documentVectors() : DocumentSetVectors = {
   
    var docVectors = DocumentSetVectors(termStrings)
    val idf = Idf()
    
    // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    for ((docid, doctf) <- tf) {                // for each doc

      var tfidf = DocumentVector()
      var length = 0f
      
      for ((term,termfreq) <- doctf) {          // for each term in doc
        if (idf.contains(term)) {               // skip if term eliminated in previous step
          val weight = termfreq * idf(term)     // otherwise, multiply tf*idf
          tfidf += (term -> weight)
          length += weight*weight
        }
      }
      
      length = math.sqrt(length).toFloat
      tfidf.transform((term,weight) => weight/length)
     
      docVectors += (docid -> tfidf)
    }
    
    return docVectors    
  }
  
}
