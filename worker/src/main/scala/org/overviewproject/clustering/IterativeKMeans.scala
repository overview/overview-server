/**
 * ItarativeKMeans.scala
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

import org.overviewproject.util.CompactPairArray
import scala.collection.mutable.{Set, ArrayBuffer, IndexedSeq}
import scala.reflect.ClassTag


// T is element type, C is centroid type
abstract class IterativeKMeans[T : ClassTag, C : ClassTag] 
  extends KMeansBase[T,C] {

  // Current centroids, cluster assignments, distortion per cluster. This state makes each instance non-reentrant
  var centroids = Seq[C]() 
  var clusters = CompactPairArray[T, Int]()
  var distortions  = Array[Double]()

  var bestCentroids = Seq[C]()
  var bestClusters = CompactPairArray[T, Int]()
  var bestFit = Double.MaxValue 
  var bestFitK = 0
    
  // track total distortion for each K tried
  var totalDistortionPerK = ArrayBuffer[Double](0.0)     // meaningless initial value for K=0
  
  def currentK = centroids.size

  // parameters. public for the moment for easy control
  def maxIterationsPerK = 15
  def maxIterationsKis2 = 15     // special case as we have rapid convergence here
  
  // Create clustering for k=1 by taking centroid of all elements, and naturally elements in this one cluster
  def InitializeCentroid(elements:Iterable[T]) : Unit = {
    centroids = Seq(mean(elements))
    bestCentroids = centroids
    // NB we don't store fits here, because fit statistic computation is always 1.0 for one cluster
  }

  // Reassign elements after a new centroid is created. This is classic k-means core loop
  def iterateAssignments(elements:Iterable[T], maxIter:Int) : Unit = {
    distortions = assignClusters(clusters, elements, centroids)
    var iter=1
    while (iter < maxIter) {
      centroids = refineCentroids(clusters, centroids)
      distortions = assignClusters(clusters, elements, centroids)
      iter+=1
    }
  }
  
  // Split one cluster into two. Take a random element for initial new centroid
  def SplitCentroid(elements:Iterable[T]) : Unit = {
    val newCentroid = mean(elements.take(1))  // ok, not a random element, first element, but shouldn't matter
    centroids = centroids :+ newCentroid

    iterateAssignments(elements, maxIterationsKis2)
  }
  
  // Generalized centroid addition, bumps K up one
  // Create a new centroid from the first element of the cluster with the worst fit (sum sq distance to all elems)
  def AddCentroid(elements:Iterable[T], k:Int) : Unit = {
    val splitIdx = distortions.indexOf(distortions.max)
    val splitElem = clusters.find(_._2 == splitIdx).get._1
    val newCentroid = mean(Seq(splitElem))

    iterateAssignments(elements, maxIterationsPerK)    
  }
    
  // Compute goodness of fit for K clusters, by technique of Pham, Divmov, Nguyen, "Selection of K in K-means clustering"
  // Lower means better fit
  def GoodnessOfFit(k:Int) : Double = {
    require(k < totalDistortionPerK.size) 
    k match {
      case 1 => 1.0
      case _ => totalDistortionPerK(k) / totalDistortionPerK(k-1)
    }
  }

  // Save best fit so far.
  def saveBestFit(k:Int) : Unit = {
    totalDistortionPerK += distortions.sum
    val fit = GoodnessOfFit(k)
    if (fit < bestFit) {
      bestCentroids = centroids
      bestClusters = clusters
      bestFit = fit
      bestFitK = k
    }    
  }
  
  // Add one cluster to the current clustering, up to K
  def cluster(elements:Iterable[T], k:Int) : Unit = {
    k match {
      case 1 => InitializeCentroid(elements)
      case 2 => SplitCentroid(elements)
      case _ => AddCentroid(elements, k) 
    }

    saveBestFit(k)
  }
  
  // Not reentrant. Just sayin'
  def apply(elements:Iterable[T], maxK:Int) : CompactPairArray[T, Int] = {    
    for (i <- 1 to maxK) {
      cluster(elements, i)
    }
    //bestClusters
    clusters
  }
}
