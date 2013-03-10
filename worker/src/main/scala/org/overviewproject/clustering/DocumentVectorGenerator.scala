/**
 * DocumentVectorGenerator.scala
 * Takes lists of terms for each document (post-lexing), generates weighted and normalized vectors for a document set
 * Currently just simple TF-IDF
 *
 * Overview Project
 *
 * Created by Jonathan Stray, July 2012
 *
 */

package org.overviewproject.clustering

import org.overviewproject.clustering.ClusterTypes._
import org.overviewproject.util.DisplayedError
import scala.collection.mutable.Map

// Error object
case class NotEnoughDocumentsError(val numDocs:Integer, val docsNeeded:Integer) extends DisplayedError("not_enough_documents_error", numDocs.toString, docsNeeded.toString)  

// Basic use: call AddDocument(docID, terms) until done, then DocumentVectors() once
class DocumentVectorGenerator {

  // --- Config ---
  var minDocsToKeepTerm = 3                   // term must be in at least this many docs or we discard it from vocabulary
  var termFreqOnly = false                    // compute TF instead of TF-IDF
  var doBigrams = false                       // generate bigram terms
  var minBigramOccurrences = 5                // throw out bigram if it has less than this many occurrences
  var minBigramLikelihood = 20                // ...or if not this many times more likely than chance to be a colocation

  // --- Data ---
  var numDocs = 0
  private var termStrings = new StringTable
  
  case class TermRecord(var useCount:Float=0, var docCount:Float=0)
  private var termCounts = Map[TermID, TermRecord]()
  
  private var idf = Map[TermID, Float]()
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

        val termIter = if (doBigrams) new BigramIterator(terms) else terms.iterator
        
        // count how many times each token appears in this doc (term frequency)
        var termcounts = DocumentVectorMap()
        for (termString <- termIter) {
          val term = termStrings.stringToId(termString)
          val prev_count = termcounts.getOrElse(term, 0f)
          termcounts += (term -> (prev_count + 1))
        }

        // divide out document length to go from term count to term frequency
        termcounts.transform((key, count) => count / terms.size.toFloat)
        
        // store the document vector in compressed form, and add it to the set of all doc vectors
        tfidf += (docId -> DocumentVector(termcounts))

        // for each unique term in this doc, update count of uses, count of docs containing term in 
        for ((term,count) <- termcounts) {
          val counts = termCounts.getOrElse(term, TermRecord(0,0))
          counts.useCount += count
          counts.docCount += 1
          termCounts += (term -> counts)
        }

        numDocs += 1
      }
    }
  }
  
  // Is the bigram a,b common enough relative to a and b alone that we should identifiy it as a colocation?
  def bigramIsLikelyEnough(ab:TermID, a:TermID, b:TermID) : Boolean = {
    true // STUB
  }
  
  // Is this bigram common enough, and likely enough to be a colocation, that we want to keep it as a feature?
  def keepBigram(term:TermID, counts:TermRecord) : Boolean = {
    val s = termStrings.idToString(term)
    val i = s.indexOf(' ')
    if (i != 0) {
      val t1 = termStrings.stringToId(s.take(i))
      val t2 = termStrings.stringToId(s.drop(i+1))
      bigramIsLikelyEnough(i, t1, t2)
    } else {
      true  // not a bigram
    }
  }

  // Drops all terms where count < minOccurencesEachTerm or count == N, and take the log of document frequency in the usual IDF way
  // Drops all brigrams that don't appear often enough, or are not likely colocations
  def keepThisTerm(term:TermID, counts:TermRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (counts.docCount < numDocs) &&
    (!doBigrams || keepBigram(term, counts))
  }
  
  // Return inverse document frequency map: term -> idf
  // Drops unneeded terms based on TermCounts, which is cleared (not needed after this)
  def Idf() = {
    if (!computedIdf) {
      computedIdf = true
      if (!termFreqOnly) {
        // Classic IDF formula. 
        // Throw out all terms that don't appear in enough docs, or all docs (were IDF will be zero), or arent a keepable bigram 
        termCounts.retain((term, counts) => keepThisTerm(term, counts))
        
        // Apply the classic IDF formula
        idf = termCounts map { case (term, counts) => (term, math.log10(numDocs / counts.docCount).toFloat) }
        
      } else {
        // Term frequency only. Still throw out terms that are too rare.
        termCounts foreach { case (term,counts) =>
          if (counts.docCount >= minDocsToKeepTerm)
            idf += (term -> 1.0f)   // equal weight to all terms
        }
      }
    }
    // save some memory; this is way bigger than idf, especially we're processing bigrams, and no longer needed
    termCounts.clear()  
    idf
  }

  // After all documents have been added, divide each term by idf value.
  // Works in place to avoid completely duplicating the set of vectors (major memory hit otherwise)
  def documentVectors(): DocumentSetVectors = {
    if (!computedDocumentVectors) {
      
      if (numDocs < minDocsToKeepTerm)
        throw new NotEnoughDocumentsError(numDocs, minDocsToKeepTerm)
      
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
