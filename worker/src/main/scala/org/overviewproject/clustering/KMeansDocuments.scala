/**
 * KMeansDocuments.scala
 * Extends KMeans class to operate on DocumentVectorMap
 * 
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import ClusterTypes._

// Short and sweet
class KMeansDocuments(protected val docVecs:DocumentSetVectors) extends KMeans[DocumentID,DocumentVector] {
  
  def distance(a:DocumentID, b:DocumentVector) : Double = {
    DistanceFn.CosineDistance(docVecs(a),b)
  }
  
  // To compute mean, we accumulate in a DocumentVectorMap, much more suited to modification than DocumentVector
  // result is not very sparse after we sum all those terms -- we get fill-in here
  def mean(elems: Iterable[DocumentID]) : DocumentVector = {
    var m = DocumentVectorMap()
    elems foreach { docId => m.accumulate(DocumentVectorMap(docVecs(docId))) }  // ...could save conversion time if we had accumulate(v:DocumentVector) 
    DocumentVector(m)
  }
}

