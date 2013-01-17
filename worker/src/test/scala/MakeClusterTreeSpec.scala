/**
 * MakeClusterTreeSpec.scala
 * 
 * Unit tests for tree generation: find connected components, build thresholded tree
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

import scala.collection.mutable.{Map, Set}
import org.overviewproject.clustering.{ConnectedComponentDocTreeBuilder, ConnectedComponents, DistanceFn}
import org.overviewproject.clustering.ClusterTypes._
import org.specs2.mutable.Specification

class GenerateClustersSpec extends Specification {
  
  // Simple graph representation that we can use for test data: a graph is Map[Int, Set[Edge]]
  case class Edge(val weight : Double, val node: Int)
  
  // Trivial test: two nodes that break apart when thresh < 0.5. Tree should have three nodes
  val twoNodeGraph = Map(
      1 -> Set(Edge(0.5, 2)),     
      2 -> Set(Edge(0.5, 1))
    )
  
  // Slightly more complicated graph, meant to test multi-stage tree generation
  val threeNodeGraph = Map(
    1 -> Set(Edge(0.2, 2)),     
    2 -> Set(Edge(0.2, 1), Edge(0.8, 3)),
    3 -> Set(Edge(0.8,2))
  )

  // Custom edge iterator that works with our simple graph types
  def reachableNodes(graph : Map[Int,Set[Edge]], thresh : Double, startNode : Int, remainingNodes : Set[Int]) : Iterable[Int] = {
    for (edge <- graph(startNode); if (edge.weight <= thresh) && (remainingNodes.contains(edge.node)))
      yield edge.node
  }
  
  "ConnectedComponents" should {
    
    "find one component" in {
      
      // break trivial graph into one or two components, depending on threshold
      val (component1, remaining1) = ConnectedComponents.SingleComponent[Int](1, Set(1,2), reachableNodes(twoNodeGraph, 0.5, _, _)) // thresh = 0.5, edge case
      component1.size must beEqualTo(2)
      remaining1.size must beEqualTo(0)
      
      val (component2, remaining2) = ConnectedComponents.SingleComponent[Int](1, Set(1,2), reachableNodes(twoNodeGraph, 0.4, _, _))
      component2.size must beEqualTo(1)
      component2.head must beEqualTo(1)  // component found should contain the node we started from
      remaining2.size must beEqualTo(1)
      
      // break three node graph should break into two components at thresh 0.5, one with two nodes and one with one
      val (component3, remaining3) = ConnectedComponents.SingleComponent[Int](1, Set(1,2,3), reachableNodes(threeNodeGraph, 0.5, _, _)) // thresh = 0.5, edge case
      component3.size must beEqualTo(2)
      remaining3.size must beEqualTo(1)    
    }
    
    "find all components" in {
      val components1 = ConnectedComponents.AllComponents[Int](Set(1,2), reachableNodes(twoNodeGraph, 0.4, _, _))
      components1.size must beEqualTo(2)
      components1.head.head must not be equalTo(components1.last.head)  // two components must have different nodes
      
      val components2 = ConnectedComponents.AllComponents[Int](Set(1,2,3), reachableNodes(threeNodeGraph, 0.5, _, _))
      components2.size must beEqualTo(2)
      val components3 = ConnectedComponents.AllComponents[Int](Set(1,2,3), reachableNodes(threeNodeGraph, 0.1, _, _))
      components3.size must beEqualTo(3)
    }
  }
  
  // Test document vectors. Only five docs, but designed to test following aspects of tree generation:
  // - a node not splitting as the threshold changes
  // - some nodes already having size = 1 before we get to the last pass (where thresh=0), some not
  // Designed so the output of each threshold pass is like so 
  //        12345
  //      12    345
  //      12   34  5
  //      12  3  4  5
  //     1  2
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
  docSet +=  (1L -> DocumentVector(DocumentVectorMap(A -> dist01, B -> dist06)))
  docSet +=  (2L -> DocumentVector(DocumentVectorMap(A -> dist01, B -> dist06)))
  docSet +=  (3L -> DocumentVector(DocumentVectorMap(B -> dist06, C -> dist02)))
  docSet +=  (4L -> DocumentVector(DocumentVectorMap(B -> dist06, C -> dist02, D -> dist04)))
  docSet +=  (5L -> DocumentVector(DocumentVectorMap(D -> dist04)))
    
 "DocTreeBuilder" should {
   "build a small tree" in {
     
    val distanceFn = (a:DocumentVector,b:DocumentVector) => DistanceFn.CosineDistance(a,b) // can't use CosineDistance because of method overloading :(
    val threshSteps = List(1,       // root contains all nodes 
                           0.7,     // no change
                           0.5,     // split 12345 => 12, 345
                           0.3,      // split 345 => 34,5
                           0.2,     // no change
                           0.1,     // split 34 => 3,4
                           0)       // leaf nodes, split 12 => 1,2
    
    val tree = new ConnectedComponentDocTreeBuilder(docSet, distanceFn).BuildTree(threshSteps)
    
    // Check that the tree has the structure in the diagram above 
    tree.toString must beEqualTo("(1,2,3,4,5, (3,4,5, (3,4, (3), (4)), (5)), (1,2, (1), (2)))")
   }
 }
}
