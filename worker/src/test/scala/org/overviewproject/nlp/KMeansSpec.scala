/**
 * KMeansSpec.scala
 * Tests both iterative (variable k) and non-iterative (fixed k) algorithms
 * 
 * Overview Project, created January 2013
 * @author Jonathan Stray
 * 
 */


package org.overviewproject.nlp

import org.specs2.mutable.Specification
import scala.collection.mutable.Set
import scala.util.Random

class KMeansSpec extends Specification {

  // Integer element type, Double centroid type -- tests case where types T != C
  trait IntKMeansOps  {
    
    def distance(a:Int, b:Double, minSoFar:Double) : Double = math.abs(a-b)
    def mean(elems: Iterable[Int]) : Double = {
      var sum = 0.0
      var len = 0
      elems.foreach { e =>
        sum += e
        len += 1
      }
      sum / len
    }
  }
  
 class IntKMeans extends KMeans[Int,Double] with IntKMeansOps
 class IntIterativeKMeans extends IterativeKMeans[Int,Double] with IntKMeansOps
  
  // This test case is challenging because of variable spacing. Iterative finds a better result, in sum sq dist sense 
  val threeClusters = Vector[Int](1,2,3,9,10,11,30,40,50)
  val threeClustersResult = Array[Int](0,0,0,1,1,1,2,2,2)
  val threeClustersVariableKResult = Array[Int](0,0,0,0,0,0,1,1,1)
  val simpleSet = List[Int](1,2,3,4,5)
  
  "centroidSeedSets" should {


    "identity when skip = 1" in {
      val km = new IntKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 1
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1), Set(2), Set(3), Set(4), Set(5)))   
    }
    
    "wrap around correctly when skip = 3" in {
      val km = new IntKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1), Set(4), Set(2), Set(5), Set(3)))   
    }
    
    "work when skip > input size" in {
      val km = new IntKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 8
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1), Set(4), Set(2), Set(5), Set(3)))         
    }
  }
  
  "assignClusters" should {
    
    "identify closest centroid for each point" in {
      val km = new IntKMeans
      val centroids = List[Double](1, 12, 40)
      val assignments = km.initialAssignments(threeClusters)
      km.assignClusters(threeClusters, assignments, centroids) 
      assignments should beEqualTo (threeClustersResult)
    }
  }
  
  "refineCentroids" should {
    "take mean of assigned clusters" in {
      val km = new IntKMeans
      val clusters = threeClustersResult
      val oldCentroids = List(0.0,0.0,0.0)
      val centroids = km.refineCentroids(threeClusters, clusters, oldCentroids)
      val tc = threeClusters
      centroids(0) should beCloseTo (tc.take(3).sum/3, 0.0001)
      centroids(1) should beCloseTo (tc.drop(3).take(3).sum/3, 0.0001)
      centroids(2) should beCloseTo (tc.drop(6).take(3).sum/3, 0.0001)
    }
  }
  
  "kMeans" should {
    "find three clusters" in {
      val km = new IntKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      val clusters = km(threeClusters, 3)
      clusters should beEqualTo (threeClustersResult)
    }

    "find two clusters using variable k algorithm" in {
      Random.setSeed(1) // we could end up with clusters permuted, so make sure we always get same result
      val km = new IntIterativeKMeans
      km.newCentroidN    = 1 
      km.newCentroidSkip = 3
      val clusters = km(threeClusters, 3) // set maxK=3, but find only two clusters
      clusters should beEqualTo (threeClustersVariableKResult)
    }

  }
}