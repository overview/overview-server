/**
 * ConnectedComponents.scala
 * Generic connected components finding algorithm. 
 * 
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.{Stack, Set}

object ConnectedComponents {

  // Takes a node, and a set of unvisited nodes, and yields all nodes we an visit next
  type EdgeEnumerationFn[T] = (T, Set[T]) => Iterable[T]

  // Returns component containing startNode, plus all nodes not in component
  def singleComponent[T](startNode: T, allNodes: Set[T], edgeEnumerator: EdgeEnumerationFn[T]): (Set[T], Set[T]) = {
    var component = Set[T](startNode) // all nodes found to be in the component so far
    val frontier = Stack[T](startNode) // nodes in the component that we have not checked the edges of
    var remaining = allNodes - startNode // nodes not yet visited

    // walk outward from each node in the frontier, until the frontier is empty or we run out of nodes
    while (!frontier.isEmpty && !remaining.isEmpty) {
      val a = frontier.pop

      for (b <- edgeEnumerator(a, remaining)) { // for every remaining we can reach from a...
        component += b
        frontier.push(b)
        remaining -= b
      }
    }

    (component, remaining)
  }

  // Find all connected componetns and do something with each 
  def foreachComponent[T](allNodes: Iterable[T], edgeEnumerator: EdgeEnumerationFn[T])(fn: Set[T]=>Unit): Unit = {
    var remaining = Set[T]() ++ allNodes  // really just allNodes.toSet, but toSet does not create a mutable set, can't use it here

    while (!remaining.isEmpty) {
      val (newComponent, leftOvers) = singleComponent(remaining.head, remaining, edgeEnumerator)
      fn(newComponent)
      remaining = leftOvers
    }
  }

  // Produce all connected components, as a set of sets
  def allComponents[T](allNodes: Iterable[T], edgeEnumerator: EdgeEnumerationFn[T]): Set[Set[T]] = {
    var components = Set[Set[T]]()
    foreachComponent(allNodes, edgeEnumerator) { 
      components += _
    }
    components
  }
}
