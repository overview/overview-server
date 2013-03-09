/**
 * KMeansDocumentComponents.scala
 * Extends KMeans, IterativeKMeans classes to cluster a set of sets of documents.
 * We find the sets by connected components, hence the name "component" for each set.
 * Really what this is is a clustering of clusters.
 * 
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import ClusterTypes._

// Representation of a document connected component (tight cluster of documents)
// For the purposes of clustering, this acts like nDocs identical docs at centroid
// Why not just the concrete DocumentComponent below? I want to let the caller decide 
// how to initialize these values, and whether or not to store the actual doc IDs in here too
abstract class AbstractDocumentComponent {
  val centroid:DocumentVectorMap
  val nDocs:Int
}

// Core document component clustering operations: distance, mean
// Separated into a trait so that we can mix it in to different K-means bases (below)
// Each component acts like nDocs identical docs at centroid
trait KMeansDocumentComponentOps[Component <: AbstractDocumentComponent] {
  
  // Distance function. Iterates over terms in component centroid, as it is likely to have less fill-in than cluster centroid
  // Same logic as KMeansDocumentOps.distance, but operates over two DocumentVectorMaps
  def distance(component:Component, b:DocumentVectorMap, minSoFar:Double) : Double = {    
    val a = component.centroid
    var dot = 0.0
    var aSqLeft = 1.0
    var bSqLeft = 1.0
   
    val  i = a.toIterator
        
    while (i.hasNext) {
      val (term, aWeight) = i.next
      
      aSqLeft -= aWeight*aWeight
      
      val bWeightOpt = b.get(term)
      if (bWeightOpt.isDefined) {         // could do this with Option.map, but I want to avoid creating a closure in this inner loop
        val bWeight = bWeightOpt.get
        bSqLeft -= bWeight*bWeight
        dot += aWeight * bWeight
      }
              
      // The maximum value dot can now reach will occur if there is one intersecting term left with all remaining weight
      // that is, it will have value sqrt(aSqLeft)*sqrt(bSqLeft) = sqrt(aSqLeft*bSqLeft)
      // If this won't get us below minSoFar, abort
      val maxPossibleDot = dot + math.sqrt(aSqLeft*bSqLeft)
      if (1.0 - maxPossibleDot >  minSoFar)
        return 1.0    // can't beat minSoFar
    }
    
    1.0 - dot
  }
  
  // To compute a mean of components, we accumulate in a DocumentVectorMap, 
  // weighting each component by the number of docs it contains
  // Note we get much fill-in here; the mean is likely not sparse
  def mean(elems: Iterable[Component]) : DocumentVectorMap = {
    var m = DocumentVectorMap()
    elems foreach { component => 
      m.multiplyAndAccumulate(component.nDocs.toFloat, component.centroid) }    
    
    val len = math.sqrt(m.values.map(v=>v*v).sum) // normalize
    m.transform((k,v) => (v / len).toFloat) 
  }
}


// Concrete document component. This is the object we actually cluster.
// Knows how to initialize itself from a list of documentIDs, which it stores compactly.
//
class DocumentComponent (_docs:Iterable[DocumentID], docVecs:DocumentSetVectors) extends AbstractDocumentComponent {

  private def documentSetCentroid(elems: Iterable[DocumentID], docVecs:DocumentSetVectors) : DocumentVectorMap = {
    var m = DocumentVectorMap()
    elems foreach { docId => m.accumulate(docVecs(docId)) }    
    val len = math.sqrt(m.values.map(v=>v*v).sum) // normalize
    m.transform((k,v) => (v / len).toFloat) 
  }

  // Save the document IDs contained in this component, in a compact read-only representation
  require(_docs.size > 0)
  val docs = _docs.toArray
  
  // Define abstract members
  val centroid = documentSetCentroid(_docs, docVecs)
  val nDocs = docs.size
}


// Now mix the Ops class, parameterized on DocumentComponent, into the IterateKMeans class, to create a concrete clustering algorithm
class IterativeKMeansDocumentComponents(protected val docVecs:DocumentSetVectors) 
  extends IterativeKMeans[DocumentComponent,DocumentVectorMap] 
  with KMeansDocumentComponentOps[DocumentComponent] {
  
}

