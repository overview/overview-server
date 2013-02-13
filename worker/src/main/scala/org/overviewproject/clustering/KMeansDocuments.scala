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

// Use DocumentVectorMap as centroid type, but just document ID as element type. 
// We reference into a set of vectors given in the ctor

class KMeansDocuments(protected val docVecs:DocumentSetVectors) extends KMeans[DocumentID,DocumentVectorMap] {
  
  // Custom cosine distance function: always index against a, as the centroid will have fill-in
  def distance(aId:DocumentID, b:DocumentVectorMap) : Double = {    
    val a = docVecs(aId)
    var dot = 0.0
    for (i <- 0 until a.length) {
      val weight = b.get(a.terms(i))
      if (weight.isDefined)
        dot += a.weights(i) * weight.get
    }
    
    1.0 - dot
  }
  
  // To compute mean, we accumulate in a DocumentVectorMap, much more suited to modification than DocumentVector
  // result is not very sparse after we sum all those terms -- we get fill-in here
  def mean(elems: Iterable[DocumentID]) : DocumentVectorMap = {
    var m = DocumentVectorMap()
    elems foreach { docId => m.accumulate(DocumentVectorMap(docVecs(docId))) }  // ...could save conversion time if we had accumulate(v:DocumentVector) 

    val len = math.sqrt(m.values.map(v=>v*v).sum) // normalize
    m.transform((k,v) => (v / len).toFloat) 
    
//    println("New centroid has " +  m.size + " terms.")
//    println("Top terms: " + m.toSeq.sortWith(_._2 > _._2).take(20))
    
    m
  }
}

