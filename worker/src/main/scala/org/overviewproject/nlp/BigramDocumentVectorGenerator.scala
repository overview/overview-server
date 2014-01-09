/**
 * BigramDocumentVectorGenerator.scala
 * Takes lists of terms for each document (post-lexing), generates weighted and normalized vectors for a document set
 * Two pass algorithm: first we discover colocations (common bigrams) as we write the text to a temp CSV file, 
 * then we read the text back in, parsing bigrams
 *
 * Overview Project
 *
 * @author Jonarhan Stray
 * Created March 2013
 *
 */

package org.overviewproject.nlp

import au.com.bytecode.opencsv.{CSVReader, CSVWriter}
import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import scala.collection.mutable.{Map, IndexedSeq}
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.util.{TempFile, FlatteningHashMap, KeyValueFlattener, Logger}


case class BigramKey(val term1:TermID, val term2:TermID = BigramKey.noTerm) {
  def isBigram = term2 != BigramKey.noTerm
}
object BigramKey {
    val noTerm = -1
}


// This is a hash map from BigramKey -> VocabRecord that is "flat",
// that is, heavily optimized to use as little memory as possible
object FlatBigrams {
  // flattener object tells FlatteningHashMap how to go from Pair[BigramKey,VocabRecord] 
  // to and from a series of 4 Ints (flatSize = 4) 
  implicit object BigramFlattener extends KeyValueFlattener[BigramKey,VocabRecord] {
    def flatSize = 4
    
    def flatten(kv:Entry, s:IndexedSeq[Int], i:Int) : Unit = {
      s(i) = kv._1.term1
      s(i+1) = kv._1.term2
      flattenValue(kv._2, s,i)
    }
    
    def unFlatten(s:IndexedSeq[Int], i:Int) : Entry = {
      (BigramKey(s(i), s(i+1)), unFlattenValue(s,i))
    }  
  
    def flattenValue(v:VocabRecord, s:IndexedSeq[Int], i:Int) : Unit = {
      s(i+2) = v.useCount
      s(i+3) = v.docCount      
    }
    
    def unFlattenValue(s:IndexedSeq[Int], i:Int) : VocabRecord = {
      VocabRecord(s(i+2),s(i+3))
    } 
  
    def keyHashCode(e:Entry) : Int = e._1.hashCode
  
    def flatKeyHashCode(s:IndexedSeq[Int], i:Int) : Int = {
      BigramKey(s(i), s(i+1)).hashCode 
    }
  
    def flatKeyEquals(k:BigramKey, s:IndexedSeq[Int], i:Int) : Boolean = { k.term1 == s(i) && k.term2 == s(i+1) }   
  }

  
  class FlatBigramVocabulary extends FlatteningHashMap[BigramKey,VocabRecord] {
  
    var totalFeatures = 0
  
    // Given a vector of feature counts, update the appropriate counts in the vocabulary table
    // Parameterized on W, the weight type, because it could be Int, TermWeight, whatever.
    def addDocument[W](featureCounts:Map[BigramKey, W])(implicit n:Numeric[W]) : Unit = {
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

}


// This generator indexes bigrams. All bigrams are stored in vocabulary and and document vectors during intermediate processing, 
// then we throw out bigrams that don't seem to be collocations, and trim doc vecs accordingly.
class BigramDocumentVectorGenerator extends TFIDFDocumentVectorGenerator {

  type WeightedBigramKey = (BigramKey,TermWeight)

  // --- Config ---
  var minBigramOccurrences:Int   = 5                // throw out bigram if it has less than this many occurrences
  var minBigramLikelihood:Double = 30               // ...or if not this many times more likely than chance to be a colocation
 
  var spoolTermSepChar = ' '                        // terms cannot contain this character, we use it as a separator in the spool file
  
  // ---- state ----
  private val docSpool = new TempFile
  private val spoolWriter = new CSVWriter(new BufferedWriter(new OutputStreamWriter(docSpool.outputStream, "utf-8")))
 
  // -- Vocabulary tables --
  
  protected var inStrings = new StringTable
  
  protected var vocab = new FlatBigrams.FlatBigramVocabulary()
  
  // ---- Colocation detection ----
  
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
  def bigramIsLikelyEnough(ab:BigramKey, a:BigramKey, b:BigramKey) : Boolean = {
    
    // Check that bigram occurs at least a minimum number of times first before computing liklihood.
    val abCount = vocab(ab).useCount
    //println(s"Checking bigram '${idToString(ab)}' with ab=$abCount")
    if (abCount < minBigramOccurrences)
      return false
      
    // Component unigrams may have already been removed from vocabulary, if they don't appear in enough docs
    // In that case bigram does not appear in enough docs either
    //println(s"Vocab contains ${idToString(a)}: ${vocab.contains(a)}, contains ${idToString(b)}: ${vocab.contains(b)}") 
    if (!vocab.contains(a) || !vocab.contains(b))
      return false
      
    //var l = colocationLikelihood(vocab(a).useCount, vocab(b).useCount, abCount, totalTerms)
    //println(s"Checking bigram '${idToString(ab)}' with ab=$abCount a=${vocab(a).useCount}, b=${vocab(b).useCount}, total=$totalTerms, likelihood $l")

    colocationLikelihood(vocab(a).useCount, vocab(b).useCount, abCount, vocab.totalFeatures) >= minBigramLikelihood
  }
  
  
  // Is this bigram common enough, and likely enough to be a colocation, that we want to keep it as a feature?
  def keepBigram(ab:BigramKey, counts:VocabRecord) : Boolean = {
    if (ab.isBigram) {
      bigramIsLikelyEnough(ab, BigramKey(ab.term1), BigramKey(ab.term2))
    } else {
      true  // not a bigram
    }
  }

 
  // --- Processing during addDocument ---
  
  // Walks over a sequence of terms, converting each to a termID, and emitting BigramKeys over every unigram and bigram
  // All term IDs are relative to inStrings
  // Side effect: adds entries to inStrings
  // Unigrams are emitted with original weights, bigrams emitted with weight 1.0
  class BigramIterator(val terms:Seq[WeightedTermString]) extends Iterator[WeightedBigramKey] {

    private val t1 = terms.iterator
    private val t2 = if (!terms.isEmpty) terms.tail.iterator else null
  
    private var lastTerm:TermID = BigramKey.noTerm
    private var lastWeight:TermWeight = 0
      
    def hasNext = t1.hasNext
    
    // flips between emitting bigrams and unigrams in such a way that t1.next is true iff we haven't finished 
    def next = {
      if (lastTerm == BigramKey.noTerm) {
        val t1TermWeight = t1.next
        lastTerm = inStrings.stringToId(t1TermWeight.term)
        lastWeight = t1TermWeight.weight                    
        if (t2.hasNext)
          (BigramKey(lastTerm, inStrings.stringToId(t2.next.term)), 1)  // emit bigram with weight 1
        else
          (BigramKey(lastTerm), lastWeight)                           // or if there isn't a next term, emit unigram
      } else {
        val term = lastTerm
        lastTerm = BigramKey.noTerm
        (BigramKey(term), lastWeight)                               // every other call, emit unigram
      }
    }
  }
  
  // Count all unigrams and bigrams appearing in a document vector
  def countBigramTerms(terms:Seq[WeightedTermString]) = {
    val termIter =  new BigramIterator(terms)
    val termCounts = Map[BigramKey, TermWeight]()
    
    for ((termKey, termWeight) <- termIter) {
      val prev_count:TermWeight = termCounts.getOrElse(termKey, 0)
      termCounts += (termKey -> (prev_count + termWeight))
    }
    termCounts
  } 

  // Drops all terms where count < minOccurencesEachTerm or count == N, and take the log of document frequency in the usual IDF way
  // Drops all brigrams that don't appear often enough, or are not likely colocations
  protected def keepThisTerm(ab:BigramKey, counts:VocabRecord) = {
    (counts.docCount >= minDocsToKeepTerm) &&
    (keepTermsWhichAppearinAllDocs || (counts.docCount < numDocs)) &&
    keepBigram(ab, counts)
  }
  
  // saves document terms to a temp file
  def spoolDocToDisk(docId: DocumentID, terms: Seq[WeightedTermString]) : Unit = {   
    
    // write docid,terms to disk. Simple format:
    //  docid,numterms
    //  terms,weight    <-- repeated numterms times
    spoolWriter.writeNext(Array(docId.toString, terms.length.toString))
    terms.foreach { tw => spoolWriter.writeNext(Array(tw.term.toString, tw.weight.toString)) }
  }
  
  // --- Processing during copmputeDocumentVectors ---
  
  // How do we display this particular bigram?
  private def bigramDisplayString(k:BigramKey) = {
    if (k.isBigram)
      inStrings.idToString(k.term1) + "_" + inStrings.idToString(k.term2)
    else
      inStrings.idToString(k.term1)
  }
    
  // Iterator that reads saved document terms back in, counting term frequency and creating new TF vectors
  def documentVectorIterator() : Iterator[(DocumentID, Map[BigramKey, TermWeight])] = {
        
    // iterator to read the documents in one at a time from CSV, converting back to document vectors
    new Iterator[(DocumentID, Map[BigramKey, TermWeight])] {
      
      spoolWriter.close()              // flushes, so we can read all we've written
      val spoolReader = new CSVReader(new BufferedReader(new InputStreamReader(docSpool.inputStream, "utf-8")))
      
      var line = spoolReader.readNext()
      
      def hasNext = line != null

      def next = {

        // Read document header: docID, numTerms 
        val id = line(0).toLong.asInstanceOf[DocumentID]
        var numTerms = line(1).toLong

        // Read each term, weight pair (there must be a more elegant way...)
        var terms = Seq[WeightedTermString]()
        while (numTerms > 0) {
          var termAndWeight = spoolReader.readNext()
          terms = terms :+ WeightedTermString(termAndWeight(0),termAndWeight(1).toDouble.asInstanceOf[TermWeight])
          numTerms -= 1
        }

        val docVec = countBigramTerms(terms)
          
        line = spoolReader.readNext()
        if (line == null)
          spoolReader.close()           // frees the temp file
          
        (id, docVec)
      }      
    }
  }
  
  // --- Required Methods  ---
  
  // addDocument updates the bigram vocabulary table, and save document to disk
  // Importantly, we do not save the document vector here 
  // (otherwise we'd use a ton of memory, as most bigrams will be discarded during colocation detection)
  def addDocumentWithWeightedTerms(docId: DocumentID, terms: Seq[WeightedTermString]) = {
  
    if (terms.size > 0) {
      val docVec = countBigramTerms(terms)
      vocab.addDocument(docVec)
      spoolDocToDisk(docId, terms)
    }
    _numDocs += 1
  }

  // Add terms without weights
  def addDocument(docId: DocumentID, terms: Seq[String]) = {
    addDocumentWithWeightedTerms(docId, terms.map(t => WeightedTermString(t,1)))
  }
  
  // Generate inverse document frequency map: term -> idf
  // Also: decide whether or not to keep each bigram. If it's not in IDF, we don't want it
  protected def computeIdf() = {
    require(vocab != null)  // we can't only call computeIdf once because it destroys vocab
    val idf = Map[BigramKey, Float]()

    vocab foreach { case (term, counts) =>
      if (!termFreqOnly) {

        // compute idf with classic log formula
        if (keepThisTerm(term, counts)) 
          idf += (term -> math.log10(numDocs / counts.docCount.toFloat).toFloat)
        
      } else {      
        
        // Term frequency only. Still throw out terms that are too rare.
        if (counts.docCount >= minDocsToKeepTerm) 
          idf += (term -> 1.0f)   // equal weight to all terms
      }
    }

    // save massive memory; this is way bigger than idf (multiple GB?) and no longer needed
    vocab = null
    
    idf
  }  
  
  // Generate final TF-IDF. 
  // Determines bigrams we want to keep, trims vocabulary, reads docs back in from temp file
  protected def computeDocVecs():Unit = {
      
    if (numDocs < minDocsToKeepTerm)
      throw new NotEnoughDocumentsError(numDocs, minDocsToKeepTerm)
    
    // Detect bigrams, generate IDF values and new outStrings table of term indices
    val idf = computeIdf() 
   
    // Read all docs back from disk, generate new term vectors
    documentVectorIterator foreach { case (docid,doctf) =>              // for each doc
      
      var docvec = DocumentVectorBuilder()
      var vecLength = 0f

      // Walk over each term in docvec, and if it's a term we're keeping, multiply by tfidf, translate to outTerms indices 
      doctf foreach { case (term,count) =>
        
        // if term not eliminated... 
        if (idf.contains(term)) {
          val weight = count * idf(term)                                  // multiply tf*idf
          val newTerm = outStrings.stringToId(bigramDisplayString(term))  // reference new string table
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
  }   
}


