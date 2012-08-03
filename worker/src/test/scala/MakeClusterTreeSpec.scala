/**
 * MakeClusterTreeSpec.scala
 * 
 * Unit tests for tree generation: find connected components, build thresholded tree
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

import clustering._
import clustering.ClusterTypes._
import org.specs2.mutable.Specification
import org.specs2.specification._
import scala.collection.mutable.{Set,Map}

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
  
  // Test document vectors. Only four docs, but designed to test following aspects of tree generation:
  // - a node not splitting as the threshold changes
  // - some nodes already having size = 1 before we get to the last pass (where thresh=0), some not
  // Designed so we get a tree like so
  //        1234
  //      12    34
  //      12   3  4
  //     1  2
  // We do this by setting:
  //    distance(1,2) = 0.1, distance(3,4) = 0.2, distance(12,34) = 0.6  
  // using three different dimensions/terms: A is only common between 1,2, B is shared between 12 and 34, C is in 3,4
  // These aren't "real" doc vectors because not normalized, but compatible with DistanceFn.CosineDistance
  
  val dist01 = math.sqrt(1 - 0.1).toFloat       // value chosen so 1 - dist01*dist01 == 0.1
  val dist02 = math.sqrt(1 - 0.2).toFloat       // etc...
  val dist06 = math.sqrt(1 - 0.6).toFloat
  
  val docSet = Map[DocumentID, DocumentVector](
      1L -> Map("A" -> dist01, "B" -> dist06),
      2L -> Map("A" -> dist01, "B" -> dist06),
      3L -> Map("B" -> dist06, "C" -> dist02),
      4L -> Map("B" -> dist06, "C" -> dist02))
      
  
 "DocTreeBuilder" should {
   "build a small tree" in {
     
    val distanceFn = DistanceFn.CosineDistance _
    val threshSteps = List(1,       // root contains all nodes 
                           0.7,     // no change
                           0.5,     // split 1234 => 12, 34 
                           0.4,     // no change
                           0.2,     // no change
                           0.1,     // split 34 => 3,4
                           0)       // leaf nodes, split 12 => 1,2
    
    val tree = new DocTreeBuilder(docSet, distanceFn).BuildTree(threshSteps)
    
    // Check that the tree has the structure in the diagram above 
    // (nodes must appear in this order because BuildTree sorts on document keys)
    tree.docs must beEqualTo(Set(1,2,3,4))
    tree.children.size must beEqualTo(2)

    val lchild = tree.children.head
    val rchild = tree.children.last
    
    lchild.docs must beEqualTo(Set(1,2))
    lchild.children.size must beEqualTo(2)
    
    val llchild = lchild.children.head
    llchild.docs must beEqualTo(Set(1))
    llchild.children must beEmpty
    
    val lrchild = lchild.children.last
    lrchild.docs must beEqualTo(Set(2))
    lrchild.children must beEmpty
    
    rchild.docs must beEqualTo(Set(3,4))
    rchild.children.size must beEqualTo(2)
    
    val rlchild = rchild.children.head
    rlchild.docs must beEqualTo(Set(3))
    rlchild.children must beEmpty
    
    val rrchild = rchild.children.last
    rrchild.docs must beEqualTo(Set(4))
    rrchild.children must beEmpty    
   }
 }
}
