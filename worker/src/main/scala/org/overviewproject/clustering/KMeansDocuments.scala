/**
 * KMeansDocuments.scala
 * Extends KMeans, IterativeKMeans classes to operate on DocumentSetVectors
 * 
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import ClusterTypes._

// Core document clustering operations: cosine distance, mean
// Separated into a trait so that we can mix it in to different K-means bases (below)
trait KMeansDocumentOps {

  protected val docVecs : DocumentSetVectors  
  
  // Custom cosine distance function: always index against a, as the centroid will have fill-in
  def distance(aId:DocumentID, b:DocumentVectorMap, minSoFar:Double) : Double = {    
    val a = docVecs(aId)
    var dot = 0.0
    var aSqLeft = 1.0
    var bSqLeft = 1.0
    var i=0
   
    while (i < a.length) {
      val aWeight = a.weights(i)
      aSqLeft -= aWeight*aWeight
      
      val weight = b.get(a.terms(i))
      if (weight.isDefined) {
        val bWeight = weight.get
        bSqLeft -= bWeight*bWeight
        dot += aWeight * bWeight
      }
              
      // The maximum value dot can now reach will occur if there is one intersecting term left with all remaining weight
      // that is, it will have value sqrt(aSqLeft)*sqrt(bSqLeft) = sqrt(aSqLeft*bSqLeft)
      // If this won't get us below minSoFar, abort
      val maxPossibleDot = dot + math.sqrt(aSqLeft*bSqLeft)
      if (1.0 - maxPossibleDot >  minSoFar)
        return 1.0    // can't beat minSoFar
        
      i+=1
    }
    
    1.0 - dot
  }
  
  // To compute mean, we accumulate in a DocumentVectorMap, much more suited to modification than DocumentVector
  // result is not very sparse after we sum all those terms -- we get fill-in here
  def mean(elems: Iterable[DocumentID]) : DocumentVectorMap = {
    var m = DocumentVectorMap()
    elems foreach { docId => m.accumulate(docVecs(docId)) }    

    val len = math.sqrt(m.values.map(v=>v*v).sum) // normalize
    m.transform((k,v) => (v / len).toFloat) 
  }

}

// Actually define the classes that can cluster documents, by combining the DocumentOps trait with various KMeans types

class KMeansDocuments(protected val docVecs:DocumentSetVectors) 
  extends KMeans[DocumentID,DocumentVectorMap]                  // DocumentVectorMap as centroid type, document ID as element type. 
  with KMeansDocumentOps {
  
}


class IterativeKMeansDocuments(protected val docVecs:DocumentSetVectors) 
  extends IterativeKMeans[DocumentID,DocumentVectorMap] 
  with KMeansDocumentOps {
  
}
