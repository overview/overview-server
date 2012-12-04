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
import org.overviewproject.clustering.ClusterTypes._

// Basic use: call AddDocument(docID, terms) until done, then DocumentVectors() once
class DocumentVectorGenerator {

  // --- Data ---
  var numDocs = 0
  private var termStrings = new StringTable
  
  private var idf = DocumentVectorMap()                        // initially holds # docs for each term, converted in place to idf later
  private var computedIdf = false
  
  private var tfidf = DocumentSetVectors(termStrings) // initially holds just tf, then multiplied in place by idf later
  private var computedDocumentVectors = false
        
  // --- Methods ---
  
  // provide limited access to our string table, so if someone has one of our vectors they can look up the IDs
  def idToString(id: TermID) = termStrings.idToString(id)
  def stringToId(s: String) = termStrings.stringToId(s)

  // Add one document. Takes a list of terms, which are pre-lexed strings. Order of terms and docs does not matter.
  // Cannot be called after documentVectors(), which "freezes" the document set 
  def addDocument(docId: DocumentID, terms: Seq[String]) = {
    require(computedDocumentVectors == false)
    require(computedIdf == false)
    
    this.synchronized {
      if (terms.size > 0) {

        // count how many times each token appears in this doc (term frequency)
        var termcounts = DocumentVectorMap()
        for (termString <- terms) {
          val term = termStrings.stringToId(termString)
          val prev_count = termcounts.getOrElse(term, 0f)
          termcounts += (term -> (prev_count + 1))
        }

        // divide out document length to go from term count to term frequency
        termcounts.transform((key, count) => count / terms.size.toFloat)
        
        // store the document vector in compressed form, and add it to the set of all doc vectors
        tfidf += (docId -> DocumentVector(termcounts))

        // for each unique term in this doc, update how many docs each term appears in (doc count)
        for (term <- termcounts.keys) {
          val prev_count = idf.getOrElse(term, 0f)
          idf += (term -> (prev_count + 1))
        }

        numDocs += 1
      }
    }
  }

  // Return inverse document frequency map: term -> idf
  // Drop all terms where count < 3 or count == N, and take the log of document frequency in the usual IDF way
  // Works in place, to save memory
  def Idf(): DocumentVectorMap = {
    if (!computedIdf) {
      computedIdf = true
      idf.retain((term, count) => (count > 2) && (count < numDocs))
      idf.transform((term, count) => math.log10(numDocs / count).toFloat)
    }
    idf
  }

  // After all documents have been added, divide each term by idf value.
  // Works in place to avoid completely duplicating the set of vectors (major memory hit otherwise)
  def documentVectors(): DocumentSetVectors = {
    if (!computedDocumentVectors) {
      computedDocumentVectors = true
      Idf()  // force idf computation
      require(computedIdf == true)

      // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
      for ((docid, doctf) <- tfidf) { // for each doc
  
        var docvec = DocumentVectorMap()
        var vecLength = 0f
  
        // Iteration over terms in PackedDocumentVector is a little awkward since it must be done by index...
        for (i <- 0 until doctf.length) { 
          val term = doctf.terms(i)
          val termfreq = doctf.weights(i)
          
          if (idf.contains(term)) { // skip if term eliminated in previous step
            val weight = termfreq * idf(term) // otherwise, multiply tf*idf
            docvec += (term -> weight)
            vecLength += weight * weight
          }
        }
  
        vecLength = math.sqrt(vecLength).toFloat
        docvec.transform((term, weight) => weight / vecLength)
  
        tfidf += (docid -> DocumentVector(docvec))  // replaces existing tf vector with tfidf vector
      }
    }

    tfidf
  }

}
