/**
 * KMeansSpec.scala
 * 
 * Overview Project, created January 2013
 * @author Jonathan Stray
 * 
 */


import org.overviewproject.clustering.KMeans
import scala.collection.mutable.Set
import org.specs2.mutable.Specification

class KMeansSpec extends Specification {

  class DoubleKMeans extends KMeans[Double] {
    
    def distance(a:Double, b:Double) : Double = math.abs(a-b)
    def mean(elems: Iterable[Double]) : Double = {
      var sum = 0.0
      var len = 0
      elems.foreach { e =>
        sum += e
        len += 1
      }
      sum / len
    }
  }
 
  val threeClusters = List[Double](1,2,3,9,10,11,30,40,50)

  val simpleSet = List[Double](1,2,3,4,5)
  
  "centroidSeedSets" should {


    "identity when skip = 1" in {
      val km = new DoubleKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 1
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1.0), Set(2.0), Set(3.0), Set(4.0), Set(5.0)))   
    }
    
    "wrap around correctly when skip = 3" in {
      val km = new DoubleKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1.0), Set(4.0), Set(2.0), Set(5.0), Set(3.0)))   
    }
    
    "work when skip > input size" in {
      val km = new DoubleKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 8
      km.centroidSeedSets(simpleSet, 5) should beEqualTo (Array(Set(1.0), Set(4.0), Set(2.0), Set(5.0), Set(3.0)))         
    }
  }
  
  "assignClusters" should {
    
    "identify closest centroid for each point" in {
      val km = new DoubleKMeans
      val centroids = List[Double](1, 12, 40)
      km.assignClusters(threeClusters, centroids) should beEqualTo (Seq(0,0,0,1,1,1,2,2,2))
    }
  }
  
  "refineCentroids" should {
    "take mean of assigned clusters" in {
      val km = new DoubleKMeans
      val assigned = List(0,0,0,1,1,1,2,2,2)
      val oldCentroids = List(0.0,0.0,0.0)
      val centroids = km.refineCentroids(threeClusters, oldCentroids, assigned, 3)
      val tc = threeClusters
      centroids(0) should beCloseTo (tc.take(3).sum/3, 0.0001)
      centroids(1) should beCloseTo (tc.drop(3).take(3).sum/3, 0.0001)
      centroids(2) should beCloseTo (tc.drop(6).take(3).sum/3, 0.0001)
    }
  }
  
  "kMeans" should {
    "find three clusters" in {
      val km = new DoubleKMeans
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      val clusters = km(threeClusters, 3)
      //println("clusters: " + clusters)
      clusters should beEqualTo (Seq(0,0,0,1,1,1,2,2,2))
    }
  }
}