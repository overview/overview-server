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
import org.overviewproject.util.{Logger, DisplayedError, StringTable}
import scala.collection.mutable.Map

// Error object
case class NotEnoughDocumentsError(val numDocs:Integer, val docsNeeded:Integer) 
  extends DisplayedError("not_enough_documents_error", numDocs.toString, docsNeeded.toString)  

// Base class object: provides basic structure, and vocabulary table.
// Does not define how document vectors are processed during addDocument
abstract class DocumentVectorGeneratorBase {

  // --- Config ---
  var minDocsToKeepTerm = 3                   // term must be in at least this many docs or we discard it from vocabulary
  var keepTermsWhichAppearinAllDocs:Boolean = false // if a term appears in all docs, IDF will be 0. Keep anyway?
  var termFreqOnly = false                    // compute TF instead of TF-IDF

  // --- Data ---
  var numDocs = 0
  protected var termStrings = new StringTable
  
  // As we read documents, we update vocab and docs
  case class TermRecord(var useCount:Int=0, var docCount:Int=0)
  protected var vocab = Map[TermID, TermRecord]()
  protected var totalTerms:Int = 0  // sum of all vocabCounts(_).useCount
  
 
  // Then we compute idf, and finally, docVecs
  private var idf = Map[TermID, Float]()
  private var computedIdf = false
  
  private var computedDocVecs = false
  private var docVecs:DocumentSetVectors = null
    
  // --- Private ---
  
  // Given a vector of term counts, update the appropriate counts in the vocabulary table
  private def updateVocab(termCounts:DocumentVectorMap):Unit = {
    // for each unique term in this doc, update count of uses, count of docs containing term in 
    for ((term,count) <- termCounts) {
      val counts = vocab.getOrElse(term, TermRecord(0,0))
      counts.useCount += count.toInt
      counts.docCount += 1
      vocab += (term -> counts)
      totalTerms += counts.useCount
    }
  }

  // Return inverse document frequency map: term -> idf
  // Drops unneeded terms based on TermCounts, which is cleared (not needed after this)
  private def computeIdf():Unit = {
    if (!termFreqOnly) {
      // Classic IDF formula. For all terms we are keeping, compute log thingy
      vocab.retain((term, counts) => keepThisTerm(term, counts))
      idf = vocab map { case (term, counts) => (term, math.log10(numDocs / counts.docCount.toFloat).toFloat) }
  
    } else {
      // Term frequency only. Still throw out terms that are too rare.
      vocab foreach { case (term,counts) =>
      if (counts.docCount >= minDocsToKeepTerm)
        idf += (term -> 1.0f)   // equal weight to all terms
      }
    }

    // save some memory; this is way bigger than idf, especially when we're processing bigrams, and no longer needed
    vocab.clear()  
  }
  
  // Create a new string table with only the terms that have been kept
  private def reducedStringTable() : StringTable = {
    val newStrings = new StringTable
    idf.keys foreach { id =>
      newStrings.stringToId(termStrings.idToString(id))   // stringToId adds this string to newStrings
    }
    newStrings
  }
  
  // After all documents have been added, divide each term by idf value.
  // Works in place to avoid completely duplicating the set of vectors (major memory hit otherwise)
  // Hence, destroys term counts, so cannot addDocument after this
  private def computeDocVecs() = {
      
    if (numDocs < minDocsToKeepTerm)
      throw new NotEnoughDocumentsError(numDocs, minDocsToKeepTerm)
    
    Idf()  // force idf computation

    // Make a new string table with only the terms we've kept, and our final DocumentSetVectors using that table
    val newStrings = reducedStringTable()
    docVecs = new DocumentSetVectors(newStrings)
   
    // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    val docIter = documentVectorIterator()
    while (docIter.hasNext) {               // for each doc

      val (docid, doctf) = docIter.next()
      var docvec = DocumentVectorMap()
      var vecLength = 0f

      // Iteration over terms in DocumentVector is a little awkward since it must be done by index...
      for (i <- 0 until doctf.length) { 
        val term = doctf.terms(i)
        val termfreq = doctf.weights(i)
        
        // if term not eliminated... 
        if (idf.contains(term)) {
          val weight = termfreq * idf(term)                         // multiply tf*idf
          val newTerm = termStrings.translateIdTo(term, newStrings) // reference new string table
          docvec += (newTerm -> weight)
          vecLength += weight * weight
        }
      }

      vecLength = math.sqrt(vecLength).toFloat
      docvec.transform((term, weight) => weight / vecLength)

      docVecs += (docid -> DocumentVector(docvec))                     // convert final vector to packed format and save
    }

    // Replace our string table with the new, reduced table. NB: invalidates idf, so we clear it to prevent misunderstandings
    Logger.info(s"Input vocabulary size ${termStrings.size}, output vocabulary size ${newStrings.size}")
    termStrings = newStrings
    idf.clear()
    
    computedDocVecs = true
  }
 
  // --- Utilities for subclasses ---
  
  // Create a basic term count vector
  // Side effects: updates string table
  protected def countTerms(terms:Iterator[String]) : DocumentVectorMap = {  
    // count how many times each token appears in this doc (term frequency)
    var termCounts = DocumentVectorMap()
    for (termString <- terms) {
      val term = termStrings.stringToId(termString)
      val prev_count = termCounts.getOrElse(term, 0f)
      termCounts += (term -> (prev_count + 1))
    }
    termCounts
  }    

  // --- Abstract members that subclasses must define ---

  // Create a vector of term counts for a single document... and store it somewhere
  // No need to update the vocab table though, we'll do that
  protected def createAndStoreDocumentVector(docId: DocumentID, terms: Seq[String]) : DocumentVectorMap
  
  // After all documents have been added, decide whether we should keep each particular term
  protected def keepThisTerm(term:TermID, counts:TermRecord) : Boolean

  // After all documents have been added, feed them one at a time into TF-IDF computation
  protected def documentVectorIterator() : Iterator[(DocumentID, DocumentVector)]

  
  // --- Public ---
  
  // provide limited access to our string table, so if someone has one of our vectors they can look up the IDs
  // Note: do not create new strings if stringToId called on non-existent string, so caller can tell if it occurs
  def idToString(id: TermID) = termStrings.idToString(id)
  def stringToId(s: String) = termStrings.stringToIdFailIfMissing(s)
  
  // Add one document.
  // Takes a list of terms, which are pre-lexed strings. Order of terms and docs does not matter.
  // Cannot be called after documentVectors(), which "freezes" the document set
  // Subclasses must define
  def addDocument(docId: DocumentID, terms: Seq[String]) = {
    if (terms.size > 0) {
      val docVec = createAndStoreDocumentVector(docId, terms)
      updateVocab(docVec)
    }
    numDocs += 1
  }

  // Compute IDF on demand
  def Idf() = {
    if (!computedIdf) {
      computeIdf()
      computedIdf = true
    }
    idf
  }
 
  // Return the final computed vectors for these documents
  def documentVectors(): DocumentSetVectors = {
    if (!computedDocVecs) {
      computeDocVecs()
      computedDocVecs = true
    }
    docVecs
  }
  
}


// "Classic" document vector generator -- all in memory, unigram parsing
class DocumentVectorGenerator extends DocumentVectorGeneratorBase {

  // --- Data ---
  private var docs = Map[DocumentID, DocumentVector]() 

  // --- Required methods ---
    
  // Create a vector of term counts for a single document... and store it somewhere
  // No need to update the vocab table though, we'll do that
  protected def createAndStoreDocumentVector(docId: DocumentID, terms: Seq[String]) : DocumentVectorMap = {
    // Count frequency of each term, and update vocab table
    val termCounts = countTerms(terms.iterator)
    
    // store the document vector in compressed form, and add it to the set of all doc vectors
    docs += (docId -> DocumentVector(termCounts))   
    
    termCounts
  }
  
  // Should we keep this particular term? This version drops all terms where count < minOccurencesEachTerm or count == N
  protected def keepThisTerm(term:TermID, counts:TermRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (keepTermsWhichAppearinAllDocs || (counts.docCount < numDocs)) 
  }

  // After all documents have been added, feed them one at a time into TF-IDF computation
  protected def documentVectorIterator() : Iterator[(DocumentID, DocumentVector)] = {
  
    // anonymous iterator class that removes from "docs" map as it feeds vectors to computeDocVecs
    // This saves a lot of memory, otherwise we end up with two copies of docvecs
    new Iterator[(DocumentID, DocumentVector)] {
      def hasNext = { docs.size > 0 }
      def next = {
        val (id, vec) = docs.head
        docs.remove(id)
        (id,vec)
      }
    }
  }
}