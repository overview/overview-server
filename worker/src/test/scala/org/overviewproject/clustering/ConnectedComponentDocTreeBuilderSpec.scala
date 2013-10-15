/**
 * ConnectedComponentDocTreeBuilderSpec.scala
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.clustering

import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.StringTable
import org.specs2.mutable.Specification

class ConnectedComponentDocTreeBuilderSpec extends Specification {
  
 
  // Test document vectors. Only five docs, but designed to test following aspects of tree generation:
  // - nodes split or not as threshold changes
  // - some nodes have size = 1 at last pass, some not
  // Designed so the output of each threshold pass is like so 
  //        12345
  //      12    345
  //      12   34  5
  //      12  3  4  5
  // We do this by setting:
  //    distance(1,2) = 0.1, distance(3,4) = 0.2, distance(34,5) = 0.4, distance(12,34) = 0.6  
  // Using four different dimensions/terms, shared between docs wherever there is an "edge" of given weight
  // These aren't "real" doc vectors because not normalized, but compatible with DistanceFn.CosineDistance
  
  val dist01 = math.sqrt(1 - 0.1).toFloat       // value chosen so 1 - dist01*dist01 == 0.1
  val dist02 = math.sqrt(1 - 0.2).toFloat       // etc...  
  val dist04 = math.sqrt(1 - 0.4).toFloat
  val dist06 = math.sqrt(1 - 0.6).toFloat
  
  val A=0; val B=1; val C=2; val D=4    // we won't actually use strings, just IDs
  
  val docSet = DocumentSetVectors(new StringTable)  
  docSet +=  (1L -> DocumentVector(DocumentVectorBuilder(A -> dist01, B -> dist06)))
  docSet +=  (2L -> DocumentVector(DocumentVectorBuilder(A -> dist01, B -> dist06)))
  docSet +=  (3L -> DocumentVector(DocumentVectorBuilder(B -> dist06, C -> dist02)))
  docSet +=  (4L -> DocumentVector(DocumentVectorBuilder(B -> dist06, C -> dist02, D -> dist04)))
  docSet +=  (5L -> DocumentVector(DocumentVectorBuilder(D -> dist04)))
    
 "ConnectedComponentDocTreeBuilder" should {
   "build a small tree" in {
     
    val threshSteps = List(1,       // root contains all nodes 
                           0.7,     // no change
                           0.5,     // split 12345 => 12, 345
                           0.3,      // split 345 => 34,5
                           0.2,     // no change
                           0.1)     // split 34 => 3,4
    
    val minComponentSize = 0
    val tree = new ConnectedComponentDocTreeBuilder(docSet).BuildFullTree(threshSteps, minComponentSize)
    
    // Check that the tree has the structure in the diagram above 
    tree.toString must beEqualTo("(1,2,3,4,5, (1,2,3,4,5, (3,4,5, (3,4, (3,4, (3,4))), (5, (5, (5)))), (1,2, (1,2, (1,2, (1,2))))))")
   }
 }
}
