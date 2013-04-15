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

import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.{EarlyOutDocVecDistance, IterativeKMeans}

// Representation of a document connected component (tight cluster of documents)
// For the purposes of clustering, this acts like nDocs identical docs at centroid
// Why not just the concrete DocumentComponent below? I want to let the caller decide 
// how to initialize these values, and whether or not to store the actual doc IDs in here too
abstract class AbstractDocumentComponent {
  val centroid:DocumentVector
  val nDocs:Int  
}

// Core document component clustering operations: distance, mean
// Separated into a trait so that we can mix it in to different K-means bases (below)
// Each component acts like nDocs identical docs at centroid
trait KMeansDocumentComponentOps[Component <: AbstractDocumentComponent] {
  
  // Distance function. Iterates over terms in component centroid, as it is likely to have less fill-in than cluster centroid
  // Same logic as KMeansDocumentOps.distance, but operates over two DocumentVectorBuilders
  def distance(component:Component, b:DocumentVectorBuilder, minSoFar:Double) : Double = {
    EarlyOutDocVecDistance(component.centroid, b, minSoFar)
  }
  
  // To compute a mean of components, we accumulate in a DocumentVectorBuilder, 
  // weighting each component by the number of docs it contains
  // Note we get much fill-in here; the mean is likely not sparse
  def mean(elems: Iterable[Component]) : DocumentVectorBuilder = {
    var m = DocumentVectorBuilder()
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

  private def documentSetCentroid(elems: Iterable[DocumentID], docVecs:DocumentSetVectors) : DocumentVector = {
    if (elems.size == 1) {
      docVecs(elems.head)   // don't copy the vector if there is only one document in this component
    } else {
      val m = DocumentVectorBuilder()
      elems foreach { docId => m.accumulate(docVecs(docId)) }    
      val len = math.sqrt(m.values.map(v=>v*v).sum) // normalize
      m.transform((k,v) => (v / len).toFloat)
      DocumentVector(m)
    }
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
  extends IterativeKMeans[DocumentComponent,DocumentVectorBuilder] 
  with KMeansDocumentComponentOps[DocumentComponent] {
  
}

