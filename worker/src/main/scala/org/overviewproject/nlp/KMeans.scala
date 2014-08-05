/**
 * KMeans.scala
 * Generic K-means clustering. Standard iterative algorithm (Lloyd's algorithm).
 *
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.nlp
import scala.collection.mutable.Set
import org.overviewproject.util.{Logger, LoopedIterator }
import org.overviewproject.util.Logger.logExecutionTime
import scala.reflect.ClassTag

// Defines interface and most basic operations for k-means clustering variants
// T is element type, C is centroid type
// Subclasses must provide quite a bit:
//  - distance() and mean()
//  - "driver" logic that initializes centroids and calls assignClusters, refineCentroids
//  - optionally, override handling for emptyCentroid()
abstract class KMeansBase[T : ClassTag, C : ClassTag] {

  // -- Abstract members, to be over-ridden by children --
  def distance(a:T, b:C, minSoFar:Double=1.0) : Double    // allow early out, if distance will be > minSoFar
  def mean(elems: Iterable[T]) : C

  // -- Basic operations, every k-means variant will need these --

  // Generate first assignments, required for call to assignClusters -- all elements assigned to centroid 0
  def initialAssignments(elements:Iterable[T]) : Array[Int] = {
    Array.fill(elements.size)(0)
  }

  // For each element, compute index of closest centroid
  // Takes previously assigned clusters, and writes to it.
  // Returns total distortion (sumsq of distances) for each cluster
  // Could do this in a more functional style with zipWithIndex, fold, etc. but this is really performance critical code
  // (profiler backs me up here --jms 2013/3/18)
  def assignClusters(elements:Iterable[T], assignments:Array[Int], centroids:Seq[C]) : Array[Double] = {
    require(assignments.size == elements.size)

    var fits = Array.fill(centroids.size)(0.0)
    var i = 0

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
      assignments(i) = closestIdx
      i += 1
      fits(closestIdx) += closestDist*closestDist
    }

    fits
  }

  // Lazily return elements in cluster
  def elementsInCluster(clusterIdx:Int, elements:IndexedSeq[T], clusters:Array[Int])  = {
    require(elements.size == clusters.size)
    for (k <- (0 until elements.size).view
        if clusters(k) == clusterIdx)
          yield elements(k)
  }

  // Handle case where no elements assigned to a centroid. Returns new centroid to use
  // Simplest implementation here: keep old centroid
  def emptyCentroid(i:Int, elements:IndexedSeq[T], clusters:Array[Int], centroids:Seq[C]) : C = {
    centroids(i)
  }

  // Given assignments of elements to clusters, compute new centroids as means of clusters
  // In case of empty cluster
  def refineCentroids(elements:IndexedSeq[T], clusters:Array[Int], centroids:Seq[C]) : Seq[C] = {
    val k = centroids.size
    Array.tabulate(k) { i =>
      val clusterElems = elementsInCluster(i, elements, clusters)
      if (!clusterElems.isEmpty)
        mean(clusterElems)
      else
        emptyCentroid(i, elements, clusters, centroids)
    }
  }
}

// Classic K-means algorithm
// T is element type, C is centroid type
//  - runs one set of iterations with fixed K.
//  - Centroid initializion by taking mean of quasi-randomly selected elements
//  - Reset empty centroids by picking random element
//  - subclasses must supply distance() and mean() for complete implementation
abstract class KMeans[T : ClassTag, C : ClassTag]
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
  override def emptyCentroid(i:Int, elements:IndexedSeq[T], clusters:Array[Int], centroids:Seq[C]) : C = {
    // println("New centroid for cluster " + i)
    val skip = seedClusterSkip * (i+1)  // +1 to avoid picking an element originally part of seed set for cluster i
    val elem = elements(skip % clusters.length)
    mean(List(elem))
  }


  // -- Main --
  def apply (elements:IndexedSeq[T], k:Int) : Array[Int] = {
    var clusters = initialAssignments(elements)

    if (!elements.isEmpty) {
      var centroids = initialCentroids(elements, k)

      val logThis = elements.size > 10000
      def logIfBig(message: String, args: Any*): Unit = {
        if (logThis) Logger.info(message, args: _*)
      }
      def logExecutionTimeIfBig[T](message: String, args: Any*)(fn: => T): T = {
        if (logThis) {
          Logger.logExecutionTime(message, args: _*)(fn)
        }
        else {
          fn
        }
      }

      logExecutionTimeIfBig("K-means on {} elements", elements.size) {
        var iterCount = 0
        var stopNow = false

        while (!stopNow) {
          logExecutionTimeIfBig("K-means assignClusters iteration {} on {} elements", iterCount, elements.size) {
            assignClusters(elements, clusters, centroids)
          }

          // Stop if we hit max iteration count
          iterCount += 1
          if (iterCount == maxIterations)
            stopNow = true

          // stop if the split failed to generate more than one cluster
          val clusterSizes = (0 until k).map(i => clusters.count(_ == i))
          if (clusterSizes.filter(_ != 0).length == 1) {
            stopNow = true
          }

          logIfBig("cluster sizes: {}", clusterSizes.toString)

          logExecutionTimeIfBig("K-means refineCentroids iteration {} on {} elements", iterCount, elements.size) {
            if (!stopNow) {
              centroids = refineCentroids(elements, clusters, centroids)
            }
          }
        }
      }
    }

    clusters
  }

}
