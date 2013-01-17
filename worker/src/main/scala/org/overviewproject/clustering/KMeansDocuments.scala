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
import ClusterTypes.{DocumentVectorMap}

// Short and sweet
class KMeansDocuments extends KMeans[DocumentVectorMap] {
  
  def distance(a:DocumentVectorMap, b:DocumentVectorMap) : Double = {
    DistanceFn.CosineDistance(a,b)
  }
  
  // will cause fill-in, though -- not very sparse after we sum all those terms
  def mean(elems: Iterable[DocumentVectorMap]) : DocumentVectorMap = {
    var m = DocumentVectorMap()
    elems foreach { m.accumulate(_) }
    m
  }
}

