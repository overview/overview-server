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
import org.overviewproject.util.{Logger, DisplayedError}
import scala.collection.mutable.Map

// Error object
case class NotEnoughDocumentsError(val numDocs:Integer, val docsNeeded:Integer) extends DisplayedError("not_enough_documents_error", numDocs.toString, docsNeeded.toString)  

// Basic use: call AddDocument(docID, terms) until done, then DocumentVectors() once
class DocumentVectorGenerator {

  // --- Config ---
  var minDocsToKeepTerm = 3                   // term must be in at least this many docs or we discard it from vocabulary
  var termFreqOnly = false                    // compute TF instead of TF-IDF

  // --- Data ---
  var numDocs = 0
  protected var termStrings = new StringTable
  
  // As we read documents, we update vocab and docs
  case class TermRecord(var useCount:Int=0, var docCount:Int=0)
  protected var vocab = Map[TermID, TermRecord]()
  protected var totalTerms:Int = 0  // sum of all vocabCounts(_).useCount
  
  private var docs = Map[DocumentID, DocumentVector]() 

  // Then we compute idf, and finally, docVecs
  private var idf = Map[TermID, Float]()
  private var computedIdf = false
  
  private var computedDocVecs = false
  private var docVecs:DocumentSetVectors = null
  
  // --- Override to change behaviour ---

  // Any pre-processing applied to terms goes here
  protected def termIterator(terms:Seq[String]):Iterator[String] = terms.iterator
  
   // Should we keep this particular term? This version drops all terms where count < minOccurencesEachTerm or count == N
  protected def keepThisTerm(term:TermID, counts:TermRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (counts.docCount < numDocs) 
  }
  
  // --- Private ---
  
 
  // Return inverse document frequency map: term -> idf
  // Drops unneeded terms based on TermCounts, which is cleared (not needed after this)
  protected def computeIdf():Unit = {
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

    // save some memory; this is way bigger than idf, especially we're processing bigrams, and no longer needed
    vocab.clear()  
  }
  
  // Create a new string table with only the terms that have been kept
  protected def reducedStringTable() : StringTable = {
    val newStrings = new StringTable
    idf.keys foreach { id =>
      newStrings.stringToId(termStrings.idToString(id))   // stringToId adds this string to newStrings
    }
    newStrings
  }
  
  // After all documents have been added, divide each term by idf value.
  // Works in place to avoid completely duplicating the set of vectors (major memory hit otherwise)
  // Hence, destroys term counts, so cannot addDocument after this
  protected def computeDocVecs() : Unit = {
      
    if (numDocs < minDocsToKeepTerm)
      throw new NotEnoughDocumentsError(numDocs, minDocsToKeepTerm)
    
    Idf()  // force idf computation

    // Make a new string table with only the terms we've kept, and our final DocumentSetVectors using that table
    val newStrings = reducedStringTable()
    docVecs = new DocumentSetVectors(newStrings)
   
    // run over the stored TF values one more time, multiplying them by the IDF values for each term, and normalizing
    while (docs.size > 0) {               // for each doc
      val (docid, doctf) = docs.head      // pop docid and terms
      docs.remove(docid)   

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

  // --- Public ---

   // provide limited access to our string table, so if someone has one of our vectors they can look up the IDs
  def idToString(id: TermID) = termStrings.idToString(id)
  def stringToId(s: String) = termStrings.stringToId(s)

  // Add one document. Takes a list of terms, which are pre-lexed strings. Order of terms and docs does not matter.
  // Cannot be called after documentVectors(), which "freezes" the document set 
  def addDocument(docId: DocumentID, terms: Seq[String]) = {
    require(computedDocVecs == false)
    require(computedIdf == false)
    
    this.synchronized {
      if (terms.size > 0) {

        val termIter = termIterator(terms)
        
        // count how many times each token appears in this doc (term frequency)
        var termcounts = DocumentVectorMap()
        for (termString <- termIter) {
          val term = termStrings.stringToId(termString)
          val prev_count = termcounts.getOrElse(term, 0f)
          termcounts += (term -> (prev_count + 1))
        }

        // for each unique term in this doc, update count of uses, count of docs containing term in 
        for ((term,count) <- termcounts) {
          val counts = vocab.getOrElse(term, TermRecord(0,0))
          counts.useCount += count.toInt
          counts.docCount += 1
          vocab += (term -> counts)
          totalTerms += counts.useCount
        }

        // divide out document length to go from term count to term frequency
        termcounts.transform((key, count) => count / terms.size.toFloat)
        
        // store the document vector in compressed form, and add it to the set of all doc vectors
        docs += (docId -> DocumentVector(termcounts))
        numDocs += 1
      }
    }
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

// This generator indexes bigrams. All bigrams are stored in vocabulary and and document vectors during intermediate processing, 
// then we throw out bigrams that don't seem to be collocations, and trim doc vecs accordingly.
class DocumentVectorGeneratorWithBigrams extends DocumentVectorGenerator {

  // --- Config ---
  var doBigrams = false                       // generate bigram terms
  var minBigramOccurrences = 5                // throw out bigram if it has less than this many occurrences
  var minBigramLikelihood = 30                // ...or if not this many times more likely than chance to be a colocation
 
  // ---- Logic ----
  
  // computes log(x**k * (1-x)**(n-k)), but rewrite this for better numerical stablilty 
  // (quite necessary, x**k is often numerically 0 which incorrectly gives -Infinity)
  def LogL(k:Double, n:Double, x:Double) : Double = {
    if (x==0 || x==1)
      -Math.log(0) // yeah, -Inf, so what?
    else 
      k*Math.log(x) + (n-k)*Math.log(1-x)
  }

  // How much more likely is it that this bigram is a true colocation than a random occurence?
  // Takes counts of first and second words, count of bigram, and total sample size
  def colocationLikelihood(count1:Int, count2:Int, count12:Int, termCount:Int) : Double = {
    val p = count2/termCount.toDouble
    val p1 = count12/count1.toDouble
    val p2 = (count2-count12)/(termCount-count1).toDouble
    -(LogL(count12, count1, p) + LogL(count2-count12, termCount-count1, p) - LogL(count12, count1, p1) - LogL(count2-count12, termCount-count1, p2))
  }

  // Is the bigram ab common enough relative to a and b alone that we should identifiy it as a colocation?
  // See Foundations of Statistical Natural Language Processing, Manning and Schutze, Ch. 5
  def bigramIsLikelyEnough(ab:TermID, a:TermID, b:TermID) : Boolean = {
    // Check that bigram occurs at least a minimum number of times first before computing liklihood.
    val abCount = vocab(ab).useCount
    if (abCount < minBigramOccurrences)
      return false
      
    // Component unigrams may have already been removed from vocabulary, if they don't appear in enough docs
    // In that case bigram does not appear in enough docs either
    if (!vocab.contains(a) || !vocab.contains(b))
      return false
      
    //var l = colocationLikelihood(vocab(a).useCount, vocab(b).useCount, abCount, totalTerms)
    //println(s"Checking bigram '${idToString(ab)}' with ab=$abCount a=${vocab(a).useCount}, b=${vocab(b).useCount}, total=$totalTerms, likelihood $l")

    colocationLikelihood(vocab(a).useCount, vocab(b).useCount, abCount, totalTerms) >= minBigramLikelihood
  }
  
  // Is this bigram common enough, and likely enough to be a colocation, that we want to keep it as a feature?
  def keepBigram(term:TermID, counts:TermRecord) : Boolean = {
    val s = termStrings.idToString(term)
    val i = s.indexOf('_')
    if (i != -1) {
      //println(s"Examining bigram '${s.take(i)}' and '${s.drop(i+1)}'")
      val t1 = termStrings.stringToId(s.take(i))
      val t2 = termStrings.stringToId(s.drop(i+1))
      bigramIsLikelyEnough(term, t1, t2)
    } else {
      true  // not a bigram
    }
  }

  // --- Overrides ---
  
  // Walk through term list as bigrams
  protected override def termIterator(terms:Seq[String]):Iterator[String] = new BigramIterator(terms)
    
  // Drops all terms where count < minOccurencesEachTerm or count == N, and take the log of document frequency in the usual IDF way
  // Drops all brigrams that don't appear often enough, or are not likely colocations
  protected override def keepThisTerm(term:TermID, counts:TermRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (counts.docCount < numDocs) &&
    keepBigram(term, counts)
  }  
}


