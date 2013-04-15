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

package org.overviewproject.nlp

import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.util.{Logger, DisplayedError}
import scala.collection.mutable.{Map, HashMap}
import scala.math.Numeric

// Error object
case class NotEnoughDocumentsError(val numDocs:Integer, val docsNeeded:Integer) 
  extends DisplayedError("not_enough_documents_error", numDocs.toString, docsNeeded.toString)  

// Base class and basic concept for DocumentVectorGenerator:  add documents one at a time, then compute the vectors when done
abstract class DocumentVectorGenerator {
   def addDocument(docId: DocumentID, terms: Seq[String]) : Unit
   def documentVectors(): DocumentSetVectors 
}

// TF-IDF vector generator: provides basic structure 
// Compute-on-demand IDF and document vector table.
// Does not define how document vectors are processed during addDocument
abstract class TFIDFDocumentVectorGenerator extends DocumentVectorGenerator {
  
  // --- Config ---
  var minDocsToKeepTerm = 3                   // term must be in at least this many docs or we discard it from vocabulary
  var keepTermsWhichAppearinAllDocs:Boolean = false // if a term appears in all docs, IDF will be 0. Keep anyway?
  var termFreqOnly = false                    // compute TF instead of TF-IDF

  // --- Private ---
  protected var _numDocs = 0

  protected var outStrings = new StringTable                                    // final output string table
  protected var docVecs:DocumentSetVectors = new DocumentSetVectors(outStrings) // final output vectors
  private var computedDocVecs = false
  
  // --- Children must implement ---  
  protected def computeDocVecs():Unit
  
  // --- Public ---

  def numDocs = _numDocs 

  // --- Main - call addDocument lots, then documentVectors ---
  
  def addDocument(docId: DocumentID, terms: Seq[String]) : Unit
  
  // Return the final computed vectors for these documents
  def documentVectors(): DocumentSetVectors = {
    if (!computedDocVecs) {
      computeDocVecs()
      computedDocVecs = true
    }
    docVecs
  }
}

// What we want to track for each term in the vocabulary
//  - total uses across all docs
//  - number of different docs this term appears in
case class VocabRecord(var useCount:Int=0, var docCount:Int=0)

// Vocabulary management object. 
// F = feature key type. How we index features; TermIDs for unigrams, BigramKeys for bigrams. Could also use straight strings.
class Vocabulary[F] extends HashMap[F,VocabRecord] {
  
  var totalFeatures = 0

  // Given a vector of feature counts, update the appropriate counts in the vocabulary table
  // Parameterized on W, the weight type, because it could be Int, TermWeight, whatever.
  def addDocument[W](featureCounts:Map[F, W])(implicit n:Numeric[W]) : Unit = {
    // for each unique term in this doc, update count of uses, count of docs containing term in 
    for ((feature,count) <- featureCounts) {
      val counts = this.getOrElse(feature, VocabRecord(0,0))
      counts.useCount += n.toInt(count)
      counts.docCount += 1
      this += (feature -> counts)
      totalFeatures += counts.useCount
    }
  }
}
  

// "Classic" document vector generator -- one pass, all in memory, unigram parsing
class UnigramDocumentVectorGenerator extends TFIDFDocumentVectorGenerator {

  // --- Data ---

  protected var idf = Map[TermID, Float]()
  protected var computedIdf = false
  
  protected var inStrings = new StringTable     // string table for terms as we read in

  protected var vocab = new Vocabulary[TermID]()
  protected var totalTerms:Int = 0  // sum of all vocabCounts(_).useCount

  // --- Internal ---
  
  // Create a basic term count vector
  // Side effects: updates string table
  protected def countTerms(terms:Iterator[String]) : DocumentVectorBuilder = {  
    // count how many times each token appears in this doc (term frequency)
    var termCounts = DocumentVectorBuilder()
    for (termString <- terms) {
      val term = inStrings.stringToId(termString)
      val prev_count = termCounts.getOrElse(term, 0f)
      termCounts += (term -> (prev_count + 1))
    }
    termCounts
  }
  
  // Create a vector of term counts for a single document... and store it somewhere
  // No need to update the vocab table though, we'll do that
  protected def createAndStoreDocumentVector(docId: DocumentID, terms: Seq[String]) : DocumentVectorBuilder = {
    // Count frequency of each term, and update vocab table
    val termCounts = countTerms(terms.iterator)
    
    // store the document vector in compressed form, and add it to the set of all doc vectors
    docVecs += (docId -> DocumentVector(termCounts))   
    
    termCounts
  }
  
  // Should we keep this particular term? This version drops all terms where count < minOccurencesEachTerm or count == N
  protected def keepThisTerm(term:TermID, counts:VocabRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (keepTermsWhichAppearinAllDocs || (counts.docCount < numDocs)) 
  }
  
  // --- Required methods ---

  def addDocument(docId: DocumentID, terms: Seq[String]) = {
    if (terms.size > 0) {
      val docVec = createAndStoreDocumentVector(docId, terms)
      vocab.addDocument(docVec)
    }
    _numDocs += 1
  }

  // Return inverse document frequency map: term -> idf
  // Drops unneeded terms based on TermCounts, which is cleared (not needed after this)
  protected def computeIdf():Unit = {
    require(vocab != null)  // we can't have computed this already; did you call Idf() after documentVectors()?
    
    if (!termFreqOnly) {
      // Classic IDF formula. For all terms we are keeping 
      // - compute idf with classic log formula
      // - create an entry in outStrings
      vocab foreach { case (term, counts) =>
        if (keepThisTerm(term, counts)) {
          idf += (term -> math.log10(numDocs / counts.docCount.toFloat).toFloat)
        }
      }
  
    } else {
      
      // Term frequency only. Still throw out terms that are too rare.
      vocab foreach { case (term,counts) =>
        if (counts.docCount >= minDocsToKeepTerm) {
          idf += (term -> 1.0f)   // equal weight to all terms
        }
      }
    }

    // save some memory; this is way bigger than idf and no longer needed
    vocab = null
  }
  
  // After all documents have been added, divide each term by idf value.
  // Works in place to avoid completely duplicating the set of vectors (major memory hit otherwise)
  // Hence, destroys term counts, so cannot addDocument after this
  protected def computeDocVecs():Unit = {
      
    if (numDocs < minDocsToKeepTerm)
      throw new NotEnoughDocumentsError(numDocs, minDocsToKeepTerm)
    
    Idf()  // force idf computation
   
    // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    docVecs foreach { case (docid,doctf) =>              // for each doc
      docVecs -= docid                                   // remove this vector, we're about to replace it
      
      var docvec = DocumentVectorBuilder()
      var vecLength = 0f

      // Iteration over terms in DocumentVector is a little awkward since it must be done by index...
      for (i <- 0 until doctf.length) { 
        val term = doctf.terms(i)
        val termfreq = doctf.weights(i)
        
        // if term not eliminated... 
        if (idf.contains(term)) {
          val weight = termfreq * idf(term)                         // multiply tf*idf
          val newTerm = inStrings.translateIdTo(term, outStrings)   // reference new string table
          docvec += (newTerm -> weight)
          vecLength += weight * weight
        }
      }

      vecLength = math.sqrt(vecLength).toFloat
      docvec.transform((term, weight) => weight / vecLength)

      docVecs += (docid -> DocumentVector(docvec))                  // convert final vector to packed format and save
    }

    // Replace our string table with the new, reduced table. NB: invalidates idf, so we clear it to prevent misunderstandings
    Logger.info(s"Input vocabulary size ${inStrings.size}, output vocabulary size ${outStrings.size}")

    // Done with all of this; only docVecs remain (and references outStrings)
    inStrings = null
    outStrings = null
    idf = null
  }

  
  // Compute IDF on demand. Exposed for testing. 
  // Note that you still won't be able to interpret the termIDs until you call documentVectors and get the stringTable from the DocumentVectors
  def Idf() = {
    if (!computedIdf) {
      computeIdf()
      computedIdf = true
    }
    idf
  }
  
  // Need also string lookup to make sense of IDF valus
  def idfStringToId(s:String) = inStrings.stringToIdFailIfMissing(s)
  def idfIdToString(t:TermID) = inStrings.idToString(t)
  
  
}