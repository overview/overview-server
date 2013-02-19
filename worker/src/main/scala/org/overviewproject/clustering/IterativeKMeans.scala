/**
 * KMeans.scala
 * Iterative K-means clustering. Starts with K=1 and adds one centroid at a time.
 * Advantage is greater stability wrt local minima, starting conditions, also potential to find optimum number of clusters
 * See Pham, Divmov, Nguyen, "An Incremental K-means algorithm", Journal of Mechanical Engineering Science, 2004   
 * 
 * Overview Project, created February 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.{Set, ArrayBuffer}
import org.overviewproject.util.CompactPairArray


// T is element type, C is centroid type
abstract class IterativeKMeans[T : ClassManifest, C : ClassManifest] 
  extends KMeansBase[T,C] {

  // Current centroids and cluster assignments. This state makes each instance non-reentrant
  var centroids = Seq[C]() 
  var clusters = CompactPairArray[T, Int]()
  var fits = Array[Double]()

  var bestCentroids = Seq[C]()
  var bestClusters = CompactPairArray[T, Int]()
    
  // track fit statistic for each K tried
  var fitAtK = ArrayBuffer[Double](0.0)     // meaningless initial value for K=0
  
  def currentK = centroids.size

  // parameters. public for the moment for easy control
  def maxIterationsPerK = 15
  def maxIterationsKis2 = 15     // special case as we have rapid convergence here
  
  // Create clustering for k=1 by taking centroid of all elements, and naturally elements in this one cluster
  def InitializeCentroid(elements:Iterable[T]) : Double = {
    centroids = Seq(mean(elements))
    bestCentroids = centroids
    // NB we don't fill clusters here... would all be zeros, but takes time and space, almost always wasted
    1.0 // Initial goodness of fit = 1
  }
  
  // Split one cluster into two. Take a random element for initial new centroid
  def SplitCentroid(elements:Iterable[T]) : Double = {
    val meanOfOne = mean(elements.take(1))  // ok, not a random element, first element, but shouldn't matter
    centroids = centroids :+ meanOfOne

    clusters = initialAssignments(elements)
    fits = assignClusters(clusters, elements, centroids)
    var iter=1
    while (iter < maxIterationsKis2) {
      centroids = refineCentroids(clusters, centroids)
      fits = assignClusters(clusters, elements, centroids)
      iter+=1
    }
   fits.sum
  }
  
  // Generalized centroid addition, bumps K up one
  def AddCentroid(elements:Iterable[T], k:Int) : Double = {
  
    // Find centroid with worst fit (sum sq distance to all elems)
    val splitIdx = fits.indexOf(fits.max)
    
    fits.sum
  }
  
  // Add one cluster to the current clustering, up to K
  def cluster(elements:Iterable[T], k:Int) : Double = {
    k match {
      case 1 => InitializeCentroid(elements)
      case 2 => SplitCentroid(elements)
      case _ => AddCentroid(elements, k) 
    }  
  }
  
  // Not reentrant. Just sayin'
  def apply(elements:Iterable[T], maxK:Int) : CompactPairArray[T, Int] = {    
    for (i <- 1 to maxK) {
      fitAtK += cluster(elements, i)
    }
    bestClusters
  }
}
