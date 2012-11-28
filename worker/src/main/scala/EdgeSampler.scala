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


// A subset of edges from the complete graph. Optimized for adding many edges, small space.
// Does not guarantee any particular order for edges attached to each node (in the CompactPairArray)
class SampledEdges extends HashMap[ DocumentID, CompactPairArray[DocumentID, Float] ] {
  
  def addEdge(a:DocumentID, b:DocumentID, dist:Float) : Unit = {
    getOrElseUpdate(a, new CompactPairArray[DocumentID, Float]) += Pair(b,dist)
  }

  // Binary search the CompactPairArray first field (Document) to see if it contains an edge to node b
  // Consider toLent the top of the array, as we'll be appending new symmetrized edges after
  private def edgeArrayContains(ar:CompactPairArray[DocumentID, Float], toLen:Int, node:DocumentID) : Boolean = {
    
      def search(key:DocumentID, ar:IndexedSeq[DocumentID], lo:Int, hi:Int) : Int = {
        if (hi <= lo) return -1;
        val mid = lo + (hi - lo)/2;
  
        ar(mid).compareTo(key) match {
          case n if n > 0 => search(key, ar, lo, mid);
          case n if n < 0 => search(key, ar, mid+1, hi);
          case 0 => mid;
        }
      }

    search(node, ar.aSeq, 0, toLen) >= 0
  }
  
  def symmetrize() : Unit = {
    // start by sorting edges for each node by id of other end
    this.transform( { case (k,v) => v.sortBy(_._1) } )  
    val lengths = this.map( { case (k,v) => (k, v.size) } )   // keep original lengths around
    
    // for each edge (a,b,dist), if (b,a,dist) doesn't exist, add it
    this foreach { case (a,v) => 
      v foreach { case (b,dist) =>
        if (!edgeArrayContains(get(b).get, lengths(b), a))    // pass lengths(b) so we only search edges that were there when we sorted
          addEdge(b, a, dist)
      }
    }
    
    this foreach { case (k,v) => v.result }   // resizes to final size, hold on to as little memory as possible
  }
}


class EdgeSampler(val docVecs:DocumentSetVectors, val distanceFn:DocumentDistanceFn) {
  
  // Store all edges that the "short edge" sampling has produced, map from doc to (doc,weight) 
  private var mySampledEdges = new SampledEdges
  
  //case class WeightPair(val id:DocumentID, val weight:TermWeight)
  case class TermProduct(val term:TermID, val weight:TermWeight, val docIdx:Int, val product:Float)
  
  // Order ProductTriples decreasing their "product", 
  // which is the weight on this term times the largest weight on this term in remainingDocs
  private implicit object TermProductOrdering extends Ordering[TermProduct] {
    def compare(a:TermProduct, b:TermProduct) = b.product compare a.product
  }
  
  // Generate a table of lists indexed by term. Each list has all docs containing that term, sorted in decreasing weight.
  // This is something like the transpose of the document vector set, and it's important to be memory efficient here,
  // hence CompactPairArray
  private def createTermTable() = {
    val t0 = System.nanoTime()    
    var totalTerms = 0
    
    // term -> array of (doc, weight)
    var termTable = Map[TermID, CompactPairArray[DocumentID,TermWeight]]()
    
    // First construct d: for each term, a list of containing docs, sorted by weight
    docVecs.foreach { case (id,vec) => 
      vec.foreach { case (term, weight) =>
        termTable.getOrElseUpdate(term, new CompactPairArray[DocumentID, TermWeight]) += Pair(id, weight)
        totalTerms += 1
      }
    }
    termTable.transform({ case (key, value) => value.sortBy(-_._2).result } ) // sort each dim by decreasing weight. result call also resizes to save space

    Logger.logElapsedTime("generated term/dimension array.", t0)
    Logger.info("StringTable size = " + docVecs.stringTable.numTerms)
    Logger.info("Vocabulary size = " + termTable.size)
    Logger.info("Average terms per doc = " + totalTerms / docVecs.size)
    
    termTable
  }
  
  // Generate a fixed number of "short" edges connecting each document.
  // For each document, we create a priority queue with one value for each term in the doc,
  // sorted by the product of the term weight by the weight of the same term in the document with the highest term value
  // We generate an edge by pulling the first item from this queue and extracting the document index, then
  // replace the item with a new product with the highest term weight among remaining docs.
  // Effectively, this samples edges in order of the largest term in their dot-product.
  private def createSampledEdges(termTable:Map[TermID, CompactPairArray[DocumentID, TermWeight]], numEdgesPerDoc:Int) : Unit = {
    val t1 = System.nanoTime()
    
    docVecs.foreach { case (id,vec) =>
      
      // pq stores one entry for each term in the doc, 
      // sorted by product of term weight times largest weight on that term in all docs
      var pq = PriorityQueue[TermProduct]()
      vec.foreach { case (term,weight) =>
        pq += TermProduct(term, weight, 0, weight* termTable(term)(0)._2)  // add term to this doc's queue
      }
      
      // Now we just pop edges out of the queue on by one
      var numEdgesLeft = numEdgesPerDoc
      while (numEdgesLeft>0 && !pq.isEmpty) {
        
        // Generate edge from this doc to the doc with the highest term product
        val termProduct = pq.dequeue
        val otherId = termTable(termProduct.term)(termProduct.docIdx)._1
        val distance = distanceFn(docVecs(id), docVecs(otherId)).toFloat
        mySampledEdges.addEdge(id, otherId, distance)
                
        // Put this term back in the queue, with a new product entry 
        // We multiply this term's weight by weight on the document with the next highest weight on this term
        val newDocIdx = termProduct.docIdx + 1
        if (newDocIdx < termTable(termProduct.term).size) {
          pq += TermProduct(termProduct.term, 
                            termProduct.weight, 
                            newDocIdx, 
                            termProduct.weight * termTable(termProduct.term)(newDocIdx)._2)
        }
        
        numEdgesLeft -= 1
      }   
    }    

    Logger.logElapsedTime("generated sampled edges.", t1)
  }
  
  // Generate the numEdgesPerDoc shortest edges going out from each document (approximately)
  // Algorithm from http://www.cs.ubc.ca/nest/imager/tr/2012/modiscotag/
  private def sampleCloseEdges(numEdgesPerDoc:Int) : Unit = {  
    val termTable = createTermTable()
    createSampledEdges(termTable, numEdgesPerDoc)    
    mySampledEdges.symmetrize
  }
  
  // --- Main ---
  def edges(numEdgesPerDoc:Int) = {
    sampleCloseEdges(numEdgesPerDoc)
    mySampledEdges
  }

}