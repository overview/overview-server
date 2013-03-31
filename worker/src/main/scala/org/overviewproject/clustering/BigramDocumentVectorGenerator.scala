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


package org.overviewproject.clustering

import org.overviewproject.clustering.ClusterTypes._

// This generator indexes bigrams. All bigrams are stored in vocabulary and and document vectors during intermediate processing, 
// then we throw out bigrams that don't seem to be collocations, and trim doc vecs accordingly.
class BigramDocumentVectorGenerator extends DocumentVectorGenerator {

  // --- Config ---
  var minBigramOccurrences:Int   = 5                // throw out bigram if it has less than this many occurrences
  var minBigramLikelihood:Double = 30               // ...or if not this many times more likely than chance to be a colocation
 
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
    (keepTermsWhichAppearinAllDocs || (counts.docCount < numDocs)) &&
    keepBigram(term, counts)
  }  
}


