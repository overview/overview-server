/**
 * BigramTermIterator.scala
 * 
 * Iterator that produces unigrams plus bigrams from a sequence of terms
 * Emits a bigram as term1_term2
 * 
 * Overview Project, March 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

class BigramIterator(val terms:Seq[String])
  extends Iterator[String] {

  private val t1 = terms.iterator
  private val t2 = if (!terms.isEmpty) terms.tail.iterator else null

  private var lastTerm:String = null
    
  def hasNext = t1.hasNext
  
  // flips between emitting bigrams and unigrams in such a way that t1.next is true iff we haven't finished 
  def next = {
    if (lastTerm == null) {
      lastTerm = t1.next
      if (t2.hasNext)
        lastTerm + "_" + t2.next    // emit bigram
      else
        lastTerm                    // or, actually there isn't a next term, emit unigram
    } else {
      val term = lastTerm
      lastTerm = null
      term                          // emit unigram
    }
  }
}
  