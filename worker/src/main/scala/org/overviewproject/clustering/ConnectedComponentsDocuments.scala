/**
 * ConnectedComponentsDocuments.scala
 * Break document set into connected components, possibly using O(N) edge sampling algorithm
 * 
 * Overview Project, created March 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.clustering.ClusterTypes._

// Produce sets of documents all connected through edges of at least some threshold
// Optionally use an edge sampler to make this not an N^2 operation
class ConnectedComponentsDocuments(protected val docVecs: DocumentSetVectors) {

  private val distanceFn = (a:DocumentVector,b:DocumentVector) => DistanceFn.CosineDistance(a,b) // can't use CosineDistance because of method overloading :(
  private var sampledEdges:SampledEdges = null

  // Produces all docs reachable from a given start doc, given thresh
  // Unoptimized implementation, scans through all possible edges (N^2 total)
  private def allReachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs: Set[DocumentID]): Iterable[DocumentID] = {
    for (
      otherDoc <- otherDocs;
      if distanceFn(docVecs(thisDoc), docVecs(otherDoc)) <= thresh
    ) yield otherDoc
  }

  // Same logic as above, but only looks through edges stored in sampledEdges
  private def sampledReachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs: Set[DocumentID]): Iterable[DocumentID] = {
    val g = sampledEdges.get(thisDoc)
    if (g.isDefined) {
      for (
        (otherDoc, distance) <- g.get if otherDocs.contains(otherDoc) if distance <= thresh
      ) yield otherDoc
    } else {
      Nil
    }
  }

  // Returns an edge walking function suitable for ConnectedComponents, using the sampled edge set if we have it
  private def createEdgeEnumerator(thresh: Double) = {
    if (sampledEdges != null)
      (doc: DocumentID, docSet: Set[DocumentID]) => sampledReachableDocs(thresh, doc, docSet)
    else
      (doc: DocumentID, docSet: Set[DocumentID]) => allReachableDocs(thresh, doc, docSet)
  }

  // ---- MAIN ----
  // NB: these methods compute from scratch each time, no caching
  
  def foreachComponent(docs:Iterable[DocumentID],thresh:Double)(fn:Set[DocumentID] => Unit) : Unit = {
    ConnectedComponents.foreachComponent[DocumentID](docs, createEdgeEnumerator(thresh)) { fn }    
  }
  
  // or just return all components at once, in a set
  def allComponents(docs:Iterable[DocumentID],thresh:Double) : Set[Set[DocumentID]] = {
    ConnectedComponents.allComponents[DocumentID](docs, createEdgeEnumerator(thresh))    
  }
  
  // Call this first if you want to use an edge sampler (lots of memory, and approximate, but O(N^2) -> ON(N) win)
  def sampleCloseEdges(numEdgesPerDoc: Int): Unit = {
    sampledEdges = new EdgeSampler(docVecs, distanceFn).edges(numEdgesPerDoc)
  }
}

