/**
 * CompactPairArraySpec.scala
 * 
 * Overview Project, created November 2012
 * @author Jonathan Stray
 * 
 */

import overview.util.CompactPairArray
import org.specs2.mutable.Specification
import org.specs2.specification._

class CompactPairArraySpec extends Specification {

  "CompaactPairArray" should {
    
    "basic operations" in {
      var cpa= new CompactPairArray[Int, Double]    // use Int, Double so we have two different types
 
      val pair1 = Pair(2,5.9)
      val pair2 = Pair(7,12.3)
      val smallestPair = Pair(-12, 1001.1)

      // Size zero after construction
      cpa.size must beEqualTo(0)
      cpa.isEmpty must beTrue

      // Add a single element
      cpa += pair1
      cpa.size must beEqualTo(1)
      cpa(0) must beEqualTo(pair1)
      cpa(1) must throwA[java.lang.ArrayIndexOutOfBoundsException]

      // Add another element
      cpa += pair2
      cpa.size must beEqualTo(2)
      cpa(1) must beEqualTo(pair2)
      cpa(2) must throwA[java.lang.ArrayIndexOutOfBoundsException]
      
      // Assign via update()
      cpa(1) = smallestPair
      cpa(1) must beEqualTo(smallestPair)
      cpa(2) must throwA[java.lang.ArrayIndexOutOfBoundsException]
          
      //  must be able to construct from varargs
      var manuallySortedCpa = CompactPairArray(smallestPair, pair1)

      // sorted() must return correct type and value (inherited via IndexSeqLike)
      var sortedCpa : CompactPairArray[Int, Double] = cpa.sorted
      // sortedCpa.sameElements(manuallySortedCpa) must beTrue   // doesn't compile, not sure why...
      sortedCpa(0) must beEqualTo(smallestPair)
    }
  }
    
}