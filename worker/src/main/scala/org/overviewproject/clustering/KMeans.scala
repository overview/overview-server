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

import overview.util.LoopedIterator
import overview.util.CompactPairArray
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

// T is element type, C is centroid type
abstract class KMeans[T : ClassManifest, C : ClassManifest] {
  
  // -- Abstract members -- 
  def distance(a:T, b:C) : Double
  def mean(elems: Iterable[T]) : C
  
  // -- Algorithm parameters -- 
  // Public for now, for easy control
  
  // Cluster centroid initialization
  var seedClusterSize = 1      // number of elements we average for each initial centroid
  var seedClusterSkip = 11 // see initializeCentroids; performance decreases linearly due to drop()
  
  // Clustering
  var maxIterations = 10        // max number of passes over all points
  
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
  
  def initialCentroids(elements:Iterable[T], k:Int) : Seq[C] = {
    centroidSeedSets(elements, k) map mean
  }

  // If a cluster ends up empty, create a new centroid. 
  // ATM just picks a quasi-random element; should probably pick element most distant from all centroids
  protected def newCentroid(clusters:CompactPairArray[T, Int], centroids:Seq[C], i:Int) : C = {
    println("New centroid for cluster " + i)
    val skip = seedClusterSkip * (i+1)  // +1 to avoid picking an element originally part of seed set for cluster i
    val elem = clusters(skip % clusters.length)
    mean(List(elem._1))
  }
  
  // For each element, compute index of closest centroid
  // Plus, for each centroid, compute index of closest element, used if no element is closest to centroid
  // Could do this in a more functional style with zipWithIndex + fold, but I don't think it would be shorter or clearer
  // plus performance matters here (this is the main inner loop on big data) so not having to think about hidden temps is nice
 
  def assignClusters(elements:Iterable[T], centroids:Seq[C]) : CompactPairArray[T, Int] = {
    val assignments = new CompactPairArray[T,Int]
    
    elements foreach { el => 
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
      assignments += Pair(el,closestIdx)
    }
    
    assignments
  }

  // Given assignments of elements to clusters, compute new centroids as means of clusters
  def refineCentroids(clusters:CompactPairArray[T, Int], centroids:Seq[C], k:Int) : Seq[C] = {
    Array.tabulate(k) { i =>
      val clusterElems = clusters.view.filter(_._2 == i).map(_._1)  // magic to lazily generate elements in cluster i
      if (clusterElems.isEmpty) 
        newCentroid(clusters, centroids, i) 
      else 
        mean(clusterElems)
    }
  }
  
  // -- Main --
  def apply (elements:Iterable[T], k:Int) : CompactPairArray[T,Int] = {
   var centroids = initialCentroids(elements, k)
   var clusters = CompactPairArray[T, Int]()
   
   println("---- starting k-means with " + elements.size + " items ----")
 //  println("initial centroids: " +  centroids)
   var iterCount = 0
   while (iterCount < maxIterations) {
     clusters = assignClusters(elements, centroids)
     centroids = refineCentroids(clusters, centroids, k)
     iterCount += 1
     
     val clusterSizes = (0 until k).map(i => clusters.filter(_._2 == i).size)
     println("cluster sizes: " + clusterSizes)
   }
   
   clusters
  }

}

