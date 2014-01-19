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

package org.overviewproject.nlp
import org.overviewproject.util.{Logger, LoopedIterator, Ranges}
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Random

// T is element type, C is centroid type
abstract class IterativeKMeans[T : ClassTag, C : ClassTag] 
  extends KMeansBase[T,C] {

  // Current centroids, cluster assignments, distortion per cluster. This state makes each instance non-reentrant
  private var centroids = Seq[C]() 
  private var clusters = Array[Int]()
  private var distortions  = Array[Double]()

  private var bestClusters = Array[Int]()
  private var bestFit = Double.MaxValue 
  private var bestFitK = 0
    
  // track total distortion for each K tried
  private var totalDistortionPerK = Array[Double](0.0)     // meaningless initial value for K=0
  
  def currentK = centroids.size

  // parameters. public for the moment for easy control
  var maxIterationsPerK = 15
  var maxIterationsKis2 = 15      // special case as we have rapid convergence here

  var minDistortion = 0.00001     // stop iterating if we hit this distortion
  var minChangePerIter = 0.001    // distortion must change at least this fraction or we stop iterating 
  
  var newCentroidSkip = 11        // subsample existing cluster by this factor...
  var newCentroidN    = 5         // ...to draw this many elements, take avg for new centroid
  
  // Debug info?
  var debugInfo = false
  
  // How many samples should we take? Not less than 1 or more than 25% of docset
  def numSamples(elements:IndexedSeq[T]) = Ranges.clip(1.0, Math.sqrt(elements.size).toInt, elements.size/4.0).toInt
  
  // Select n samples from a seq, at regular intervals. Set skip to a prime to prevent repetition when we wrap past the end of input.
  def subSampleIndexed(elements:IndexedSeq[T], start:Int, skip:Int, n:Int) : Seq[T] = {
    val samples = new ArrayBuffer[T](n) 
    var i = start
    var left = n
    while (left > 0) {
      samples += elements(i % elements.size)  // wrap around end
      i += skip
      left -= 1
    }  
    samples
  }
  
  // when the seq is not indexed, use this
  def subSampleSeq(elements:Seq[T], start:Int, skip:Int, n:Int) : Seq[T] = {
    val samples = new ArrayBuffer[T](n)
    val iter = new LoopedIterator[T](elements.iterator)
    iter.drop(start)
    var left = n
    while (left > 0) {
      samples += iter.next
      iter.drop(skip)
      left -= 1
    }
    samples
  }
  
  // Reassign elements after a new centroid is created. This is classic k-means core loop
  def iterateAssignments(elements:IndexedSeq[T], maxIter:Int) : Unit = {
    distortions = assignClusters(elements, clusters, centroids)
    var stopNow = false
    var iter=1
    while (iter < maxIter && !stopNow) {

      if (debugInfo) {
        val clusterSizes = (0 until totalDistortionPerK.size).map(i => clusters.count(_ == i))
        Logger.debug("-- -- after iteration " + iter + " sizes: " + clusterSizes)
      }

      centroids = refineCentroids(elements, clusters, centroids)
      val distortions2 = assignClusters(elements, clusters, centroids)
      
      // If the distortions changed less then percentage threshold, break
      val dis = distortions.sum
      val dis2 = distortions2.sum
      val disChange = (dis-dis2)/dis
      if ((dis2 < minDistortion) || (math.abs(disChange) < minChangePerIter))
        stopNow = true

      distortions = distortions2
      iter+=1

      if (debugInfo)
        Logger.debug("-- -- iteration " + iter + " changed distortion by " + disChange)
        
    }
  }
  
  // Create clustering for k=1 by taking centroid of all elements, and naturally all elements in this one cluster
  // Must compute the initial distortion too, we do this as we create the initial assignments array
  def InitializeCentroid(elements:IndexedSeq[T]) : Unit = {
    centroids = Seq(mean(elements))
    clusters = Array.fill(elements.size)(0)
    
    var distortion = 0.0          // yeah, could do functional magic, but need performance here (lack of temps, function calls)
    elements.foreach { el =>
      val d = distance(el, centroids(0))
      distortion += d*d
    }
    distortions = Array(distortion)
  }
  
/*
  // Split one cluster into two. Take a random element for initial new centroid
  def SplitCentroid(elements:IndexedSeq[T]) : Unit = {
    val newCentroid = mean(subSampleIndexed(elements, 0, newCentroidSkip, numSamples(elements)))  // ok, not a random element, but shouldn't matter
    centroids = centroids :+ newCentroid

    iterateAssignments(elements, maxIterationsKis2)
  }
*/

  // Generalized centroid addition, bumps K up one
  // Choose a number of points randomly, weighted by distance to nearest cluster center 
  // (farther distance makes a point more likely to be chosen) Then take the mean. 
  // This is akin to the k-means++ algorithm but we take the mean of multiple points to 
  // make it more robust to the many, many ouliers in the data set. In particular, if we
  // picked only one point which had few or no words in common with all other docs, it might
  // never be the closest center to any other document and so would remain an outlier forever.
  // This is a particular problem of the very high-dimensional spaces we work in.
  def AddCentroid(elements:IndexedSeq[T], k:Int) : Unit = {
    if (debugInfo)
      Logger.debug("-- -- distortions: " + distortions.mkString(","))
      
    val totalDistSquared = distortions.sum  
    val elementsToPick = numSamples(elements)

    // Loop over all elements. Add each to list with probability proportional to squared distance
    // Set the scaling so we expect to pick elementsToPick items, on average
    var samples = new ArrayBuffer[T](elementsToPick)
    var i = 0
    while (i < elements.length) {
      val elemDist = distance(elements(i), centroids(clusters(i)))
      val p = elemDist*elemDist*elementsToPick/totalDistSquared
      if (p > Random.nextDouble)
        samples += elements(i)
      i += 1
    }

    if (debugInfo)
      Logger.debug(s"-- -- chose ${samples.size} samples for new centroid, wanted $elementsToPick")

    // If we ended up with too few samples (perhaps none) add random elements
    while (samples.size < elementsToPick)
      samples += elements(Random.nextInt(elements.length))

    centroids = centroids :+ mean(samples)
  }
    
  // Compute goodness of fit for K clusters, by technique of Pham, Divmov, Nguyen, "Selection of K in K-means clustering"
  // Becuase dimension is very high their formula is particularly simple, 
  // just the ratio of distortion of this iteration versus the last
  // Lower means better fit
  def goodnessOfFit(k:Int) : Double = {
    require(k < totalDistortionPerK.size) 
    k match {
      case 1 => 1.0
      case _ => totalDistortionPerK(k) / totalDistortionPerK(k-1)
    }
  }

  // Save best fit so far, measured by goodnessOfFit statistic
  def saveBestFit(k:Int, maxK:Int) : Unit = {
    totalDistortionPerK(k) = distortions.sum
    val fit = goodnessOfFit(k)
    if (fit < bestFit) {
      if (k < maxK) 
        bestClusters = clusters.clone() // save copy of cluster assignments, as clusters will be over-written
      else
        bestClusters = clusters // suppress copy if this is the last iteration
      bestFit = fit
      bestFitK = k
    }    
  }
  
  // Add one cluster to the current clustering, up to K
  def cluster(elements:IndexedSeq[T], k:Int, maxK:Int) : Unit = {
    
    if (debugInfo)
      Logger.debug("-- Adding centroid " + k)
      
    if (k==1) {
      InitializeCentroid(elements)
    } else {
      AddCentroid(elements, k) 
      iterateAssignments(elements, maxIterationsPerK)    
    }

    if (debugInfo) {
      val clusterSizes = (0 until k).map(i => clusters.count(_ == i))
      Logger.debug("-- cluster sizes: " + clusterSizes)
    }

    saveBestFit(k, maxK)
  }
  
  // Not reentrant, because of all the internal state.
  def apply(elements:IndexedSeq[T], maxK:Int) : Array[Int] = {

    // Boundary cases: no elements, one element, one cluster
    require(elements.size > 0)
    if (elements.size == 1) {
      return new Array[Int](1)  // will assign the only element to cluster 0
    }
    if (maxK == 1) {
      return new Array[Int](elements.size) // assign all elements to cluster 0
    }
    
    // reset best fit trackers
    totalDistortionPerK = Array.fill(maxK+1)(0.0)
    bestFit = Double.MaxValue 
    
    // debugInfo = true

    if (debugInfo)
      Logger.info("---- Starting KMI with " + elements.size + " elements ----")
      
    for (i <- 1 to maxK) {
      cluster(elements, i, maxK)
    }

    if (debugInfo)
      Logger.info("---- Finished KMI, goodness of fits: " + (1 to maxK).map(goodnessOfFit).mkString(","))
    
    require(bestClusters.size == elements.size) // minor sanity check
    bestClusters
  }
}
