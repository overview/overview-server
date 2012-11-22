/**
 * EdgeSampler.scala
 *
 * Given a set of document vectors, try to generate the "close" edges, a fixed number for each document.
 * This is possible by ordering the edge generation by the largest term in the dot product between the
 * current doc and any other doc. 
 * 
 * Based on an algorithm by Stephen Ingram, in http://www.cs.ubc.ca/cgi-bin/tr/2012/TR-2012-01
 *
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.clustering

import scala.collection.mutable.{ Set, Stack, PriorityQueue, Map, HashMap}
import ClusterTypes._
import overview.util.Logger
import overview.util.CompactPairArray
//import scala.collection.mutable.AddingBuilder

class EdgeSampler(val docVecs:DocumentSetVectors, val distanceFn:DocumentDistanceFn) {
  
  // Store all edges that the "short edge" sampling has produced, map from doc to (doc,weight) 
  private var mySampledEdges = new SampledEdges
  
  // utility function to add a single edge to sampledEdges, which is a bit more of pain than it should be
  private def addSymmetricEdge(a:DocumentID, b:DocumentID, distance:Double) : Unit = {
    val aEdges = mySampledEdges.getOrElse(a, Map[DocumentID, Double]())
    aEdges += (b -> distance)
    mySampledEdges += (a -> aEdges)
    val bEdges = mySampledEdges.getOrElse(b, Map[DocumentID, Double]())
    bEdges += (a -> distance)
    mySampledEdges += (b -> bEdges)
  }
  
  //case class WeightPair(val id:DocumentID, val weight:TermWeight)
  case class TermProduct(val term:TermID, val weight:TermWeight, val docIdx:Int, val product:Float)
  
  // Order ProductTriples decreasing their "product", 
  // which is the weight on this term times the largest weight on this term in remainingDocs
  private implicit object TermProductOrdering extends Ordering[TermProduct] {
    def compare(a:TermProduct, b:TermProduct) = (b.product - a.product).toInt
  }
  
  // Generate the numEdgesPerDoc shortest edges going out from each document (approximately)
  // Algorithm from http://www.cs.ubc.ca/nest/imager/tr/2012/modiscotag/
  // Basic idea is we create a table indexed by term, listing all docs containing that term in decreasing weight
  // Then for each document in term, we create a priority queue with one value for each term in the doc,
  // sorted by the product of the term weight by the weight of the same term in the document with the highest term value
  // We generate an edge by pulling the first item from this queue and extracting the document index, then
  // replace the item with a new product with the highest term weight among remaining docs.
  // Effectively, this samples edges in order of the largest term in their dot-product.
  private def sampleCloseEdges(numEdgesPerDoc:Int = 200) : Unit = {  
    val t0 = System.nanoTime()    
    var totalTerms = 0
    
    // For each dimension (term), list of docs containing that term, and weight on each doc, sorted by weight
    var d = Map[TermID, CompactPairArray[DocumentID,TermWeight]]()
    
    // First construct d: for each term, a list of containing docs, sorted by weight
    docVecs.foreach { case (id,vec) => 
      vec.foreach { case (term, weight) =>
        d.getOrElseUpdate(term, new CompactPairArray[DocumentID, TermWeight]) += Pair(id, weight)
        totalTerms += 1
      }
    }
    d.transform({ case (key, value) => value.sortBy(-_._2).result } ) // sort each dim by decreasing weight. result call also resizes to save space

    Logger.info("StringTable size = " + docVecs.stringTable.numTerms)
    Logger.info("Vocabulary size = " + d.size)
    Logger.info("Average terms per doc = " + totalTerms / docVecs.size)

    Logger.logElapsedTime("generated term/dimension array.", t0)
    val t1 = System.nanoTime()
    
    // Now use d to produce numEdgesPerDoc "short" edges starting from each document
    docVecs.foreach { case (id,vec) =>
      
      // pq stores one entry for each term in the doc, 
      // sorted by product of term weight times largest weight on that term in all docs
      var pq = PriorityQueue[TermProduct]()
      vec.foreach { case (term,weight) =>
        pq += TermProduct(term, weight, 0, weight*d(term)(0)._2)  // add term to this doc's queue
      }
      
      // Now we just pop edges out of the queue on by one
      var numEdgesLeft = numEdgesPerDoc
      while (numEdgesLeft>0 && !pq.isEmpty) {
        
        // Generate edge from this doc to the doc with the highest term product
        val termProduct = pq.dequeue
        val otherId = d(termProduct.term)(termProduct.docIdx)._1
        val distance = distanceFn(docVecs(id), docVecs(otherId))
        addSymmetricEdge(id, otherId, distance)
                
        // Put this term back in the queue, with a new product entry 
        // We multiply this term's weight by weight on the document with the next highest weight on this term
        val newDocIdx = termProduct.docIdx + 1
        if (newDocIdx < d(termProduct.term).size) {
          pq += TermProduct(termProduct.term, termProduct.weight, newDocIdx, termProduct.weight*d(termProduct.term)(newDocIdx)._2)
        }
        
        numEdgesLeft -= 1
      }   
    }
    
    Logger.logElapsedTime("generated sampled edges.", t1)
  }
  
  // --- Main ---
  def edges = {
    sampleCloseEdges()
    mySampledEdges
  }

}