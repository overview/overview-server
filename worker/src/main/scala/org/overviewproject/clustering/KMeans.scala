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

import scala.Array.fallbackCanBuildFrom
import scala.collection.mutable.Set
import org.overviewproject.util.{CompactPairArray, Logger, LoopedIterator }
import org.overviewproject.util.Logger.logExecutionTime

// Defines interface and most basic operations for k-means clustering variants
// T is element type, C is centroid type
abstract class KMeansBase[T : ClassManifest, C : ClassManifest] {
  
  // -- Abstract members, to be over-ridden by children -- 
  def distance(a:T, b:C, minSoFar:Double=1.0) : Double    // allow early out, if distance will be > minSoFar 
  def mean(elems: Iterable[T]) : C
    
  // -- Basic operations, every k-means variant will need these --
  
  // For each element, compute index of closest centroid
  // Could do this in a more functional style with zipWithIndex, fold, etc. but this is really performance critical code
  // (profiler backs me up here --jms 2013/3/18)
  def assignClusters(elements:Iterable[T], centroids:Seq[C]) : CompactPairArray[T, Int] = {
    val assignments = new CompactPairArray[T,Int]
    assignments.sizeHint(elements.size)
    
    elements foreach { el => 
      var cItr = centroids.iterator
      var closestDist = distance(el, cItr.next)
      var closestIdx = 0
      var idx = 1
      cItr foreach { c =>
        val cDst = distance(el, c, closestDist)
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

  // Handle case where no elements assigned to a centroid. Returns new centroid to use
  // Simplest implementation here: keep old centroid
  def emptyCentroid(i:Int, clusters:CompactPairArray[T, Int], centroids:Seq[C]) : C = {
    centroids(i)
  }

  // Given assignments of elements to clusters, compute new centroids as means of clusters
  // In case of empty cluster 
  def refineCentroids(clusters:CompactPairArray[T, Int], centroids:Seq[C], k:Int) : Seq[C] = {
    Array.tabulate(k) { i =>
      val clusterElems = clusters.view.filter(_._2 == i).map(_._1)  // magic to lazily generate elements in cluster i
      if (!clusterElems.isEmpty)
        mean(clusterElems)
      else
        emptyCentroid(i, clusters, centroids)
    }
  } 
}

// Classic K-means algorithm 
// T is element type, C is centroid type
//  - runs one set of iterations with fixed K.
//  - Centroid initializion by taking mean of quasi-randomly selected elements
//  - Reset empty centroids by picking random element
//  - subclasses must supply distance() and mean() for complete implementation
abstract class KMeans[T : ClassManifest, C : ClassManifest] 
  extends KMeansBase[T,C] {
  
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
  override def emptyCentroid(i:Int, clusters:CompactPairArray[T, Int], centroids:Seq[C]) : C = {
//    println("New centroid for cluster " + i)
    val skip = seedClusterSkip * (i+1)  // +1 to avoid picking an element originally part of seed set for cluster i
    val elem = clusters(skip % clusters.length)
    mean(List(elem._1))
  }
  

  // -- Main --
  def apply (elements:Iterable[T], k:Int) : CompactPairArray[T,Int] = {   
    var clusters = CompactPairArray[T, Int]()

    if (!elements.isEmpty) {
      var centroids = initialCentroids(elements, k)
     
      
      var logThis = elements.size > 10000
     
      logExecutionTime("K-means on " + elements.size + " elements", logThis) {

        var iterCount = 0
        var stopNow = false     
  
        while (!stopNow) {
  
          logExecutionTime("K-means assignClusters iteration " + iterCount + " on " + elements.size + " elements", logThis) {
            clusters = assignClusters(elements, centroids)
          }  
         
          // Stop if we hit max iteration count
          iterCount += 1
          if (iterCount == maxIterations)
            stopNow = true
         
          // stop if the split failed to generate more than one cluster
          val clusterSizes = (0 until k).map(i => clusters.filter(_._2 == i).size)
          if (clusterSizes.filter(_ != 0).length == 1) {
            stopNow = true
          }
         
          if (logThis)
            Logger.info("cluster sizes: " + clusterSizes)
           
          logExecutionTime("K-means refineCentroids iteration " + iterCount + " on " + elements.size + " elements", logThis) {
            if (!stopNow)
              centroids = refineCentroids(clusters, centroids, k)
          }
        }
      }
    }
   
    clusters
  }

}

