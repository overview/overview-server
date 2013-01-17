/**
 * KMeans.scala
 * Generic K-means clustering. Standard iterative algorithm (Lloyd's algorithm). 
 * 
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set

// Iterator that loops infinitely over an underlying (presumably finite) iterator
// Resets when it hits the end by calling makeIter. Can be empty if makeIter returns empty iter.
class LoopedIterator[T](makeIter: => Iterator[T]) extends Iterator[T] {
  
  private var current = makeIter
  private val trulyEmpty = current.isEmpty
  
  def hasNext = !trulyEmpty
  def next : T = {
    if (!current.hasNext && !trulyEmpty)
      current = makeIter
    current.next
  } 
  
}

abstract class KMeans[T : ClassManifest] {
  
  // -- Abstract members -- 
  def distance(a:T, b:T) : Double
  def mean(elems: Iterable[T]) : T
  
  // -- Algorithm parameters -- 
  // Public for now, for easy control
  
  // Cluster centroid initialization
  var seedClusterSize = 10      // number of elements we average for each initial centroid
  var seedClusterSkip = 11 // see initializeCentroids; performance decreases linearly due to drop()
  
  // Clustering
  var maxIterations = 5        // max number of passes over all points
  
  // -- Implementation --
  
  // Generate a set of seed points for each cluster, from which we will produce initial means
  // Takes a small number of deterministically selected elements, hopefully fairly scattered.
  // What it actually does is distribute every p-th element among k sets, wrapping at end of elements
  def centroidSeedSets(elements:Iterable[T], k:Int) : Array[Set[T]] = {
    
    val samplesPerCentroid = math.min(seedClusterSize, math.ceil(elements.size/k).toInt) // 10 per, unless not enough elements
    val p = seedClusterSkip
    
    val loopedElem = new LoopedIterator[T](elements.iterator)
    var meanElements = Array.fill(k) { Set[T]() }
    
    for (i <- 0 to (samplesPerCentroid * k)-1) {
      meanElements(i%k) += loopedElem.next
      loopedElem.drop(p-1)
    }
    
    meanElements
  }
  
  def initialCentroids(elements:Iterable[T], k:Int) : Seq[T] = {
    centroidSeedSets(elements, k) map mean
  }

  
  // For each element, compute index of closest centroid
  // Plus, for each centroid, compute index of closest element, used if no element is closest to centroid
  // Could do this in a more functional style with zipWithIndex + fold, but I don't think it would be shorter or clearer
  // plus performance matters here (this is the main inner loop on big data) so not having to think about hidden temps is nice

  def assignClusters(elements:Seq[T], centroids:Seq[T]) : Seq[Int] = {
    elements map { el => 
      var cItr = centroids.iterator
      var closestDist = distance(el, cItr.next)
      var closestIdx = 0
      var idx = 1
      cItr foreach { c =>
        val cDst = distance(el, c)
        if (cDst < closestDist) {
          closestDist = cDst
          closestIdx = idx
       }
       idx += 1
      }
      closestIdx
    }
  }

  // Given assignments of elements to clusters, compute new centroids as means of clusters
  def refineCentroids(elements:Seq[T], centroids:Seq[T], assigned:Seq[Int], k:Int) : Seq[T] = {
    Array.tabulate(k) { i =>
      val clusterElems = elements.view.zip(assigned).filter(_._2 == i).map(_._1)  // magic to lazily generate elements in cluster i
      if (clusterElems.isEmpty) 
        mean(List(centroids(i),centroids((i+seedClusterSkip) % k)))
      else 
        mean(clusterElems)
    }
  }
  
  // -- Main --
  def apply (elements:Seq[T], k:Int) : Seq[Int] = {
   var centroids = initialCentroids(elements, k)
   var clusters = Seq[Int]()
   
 //  println("initial centroids: " +  centroids)
   var iterCount = 0
   while (iterCount < maxIterations) {
     clusters = assignClusters(elements, centroids)
     centroids = refineCentroids(elements, centroids, clusters, k)
     iterCount += 1
 //    println("clusters: " + clusters)
 //    println("centroids: " +  centroids)
   }
   
   clusters
  }

}

