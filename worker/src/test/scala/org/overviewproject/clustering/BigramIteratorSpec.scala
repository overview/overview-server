/**
 * BigramIteratorSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

import org.overviewproject.clustering._
import org.specs2.mutable.Specification

class BigramIteratorSpec extends Specification {
  "BigramIterator" should {

    "handle empty input" in {
      val empty = Seq[String]()
      val b = new BigramIterator(empty)
      b.hasNext should beFalse
    }
    
    "emit one unigram for one word input" in {
      val b = new BigramIterator(Seq("word"))
      b.toSeq should beEqualTo(Seq("word"))
    }
    
    "emit two unigrams and one bigram for two word input" in {
      val b = new BigramIterator(Seq("a","b"))
      b.toSeq should beEqualTo(Seq("a_b","a","b"))
    }
    
    "handle multiple word input" in {
      val b = new BigramIterator(Seq("a","b","c","d"))
      b.toSeq should beEqualTo(Seq("a_b","a","b_c","b","c_d","c","d"))
    }
    
  }
}