/**
 * ConnectedComponentsSpec.scala
 *  
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

import scala.collection.mutable.{Map, Set}
import org.overviewproject.clustering.ConnectedComponents
import org.specs2.mutable.Specification

class ConnectedComponentsSepc extends Specification {
  
  // Simple graph representation that we can use for test data: a graph is Map[Int, Set[Edge]]
  case class Edge(val weight : Double, val node: Int)
  
  // Trivial test: two nodes that break apart when thresh < 0.5. Tree should have three nodes
  val twoNodeGraph = Map(
      1 -> Set(Edge(0.5, 2)),     
      2 -> Set(Edge(0.5, 1))
    )
  
  // Slightly more complicated graph, breaks apart at thresholds of 0.8 and 0.3
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
      val (component1, remaining1) = ConnectedComponents.singleComponent[Int](1, Set(1,2), reachableNodes(twoNodeGraph, 0.5, _, _)) // thresh = 0.5, edge case
      component1.size must beEqualTo(2)
      remaining1.size must beEqualTo(0)
      
      val (component2, remaining2) = ConnectedComponents.singleComponent[Int](1, Set(1,2), reachableNodes(twoNodeGraph, 0.4, _, _))
      component2.size must beEqualTo(1)
      component2.head must beEqualTo(1)  // component found should contain the node we started from
      remaining2.size must beEqualTo(1)
      
      // break three node graph should break into two components at thresh 0.5, one with two nodes and one with one
      val (component3, remaining3) = ConnectedComponents.singleComponent[Int](1, Set(1,2,3), reachableNodes(threeNodeGraph, 0.5, _, _)) // thresh = 0.5, edge case
      component3.size must beEqualTo(2)
      remaining3.size must beEqualTo(1)    
    }
    
    "find all components" in {
      val components1 = ConnectedComponents.allComponents[Int](Set(1,2), reachableNodes(twoNodeGraph, 0.4, _, _))
      components1.size must beEqualTo(2)
      components1.head.head must not be equalTo(components1.last.head)  // two components must have different nodes
      
      val components2 = ConnectedComponents.allComponents[Int](Set(1,2,3), reachableNodes(threeNodeGraph, 0.5, _, _))
      components2.size must beEqualTo(2)
      val components3 = ConnectedComponents.allComponents[Int](Set(1,2,3), reachableNodes(threeNodeGraph, 0.1, _, _))
      components3.size must beEqualTo(3)
    }
  }
}
