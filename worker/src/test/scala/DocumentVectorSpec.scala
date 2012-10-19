/**
 * DocumentVectorSpec.scala
 * 
 * Unit tests for DocumentVectorMap and DocumentVector objects. 
 * 
 * Overview Project, created October 2012
 * @author Jonathan Stray
 * 
 */

import org.specs2.mutable.Specification
import org.specs2.specification._
import overview.clustering.ClusterTypes._

class PackedDocumentVector extends Specification {
  
  "DocumentVectorSpec" should {

    "represent a document vector in sorted order" in {
      val d = DocumentVectorMap(1->0.4f, 2->0.1f, 10->0.01f, 5->0.2f)
      val p = DocumentVector(d)
      
      p.length must beEqualTo(d.size)
      p.terms must beEqualTo(Array[TermID](1, 2, 5, 10))
      p.weights must beEqualTo(Array[TermWeight](0.4f, 0.1f, 0.2f, 0.01f))      
    }
    
    "fail if given ctor arguments of differing length" in {
      val twoTerms = Array[TermID](1,2)
      val threeWeights = Array[TermWeight](0.1f, 0.2f, 0.3f)
      new overview.clustering.ClusterTypes.DocumentVector(twoTerms, threeWeights) must throwA[java.lang.IllegalArgumentException]
    }
  }    

}
