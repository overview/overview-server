/**
 * ConnectedComponents.scala
 * Generic connected components finding algorithm. 
 * 
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.clustering
import scala.collection.mutable.{Stack, Set}

object ConnectedComponents {

  // Takes a node, and a set of unvisited nodes, and yields all nodes we an visit next
  type EdgeEnumerationFn[T] = (T, Set[T]) => Iterable[T]

  // Returns component containing startNode, plus all nodes not in component
  def SingleComponent[T](startNode: T, allNodes: Set[T], edgeEnumerator: EdgeEnumerationFn[T]): (Set[T], Set[T]) = {
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

  // Produce all connected components
  def AllComponents[T](allNodes: Set[T], edgeEnumerator: EdgeEnumerationFn[T]): Set[Set[T]] = {
    var components = Set[Set[T]]()
    var remaining = allNodes

    while (!remaining.isEmpty) {
      val (newComponent, leftOvers) = SingleComponent(remaining.head, remaining, edgeEnumerator)
      components += newComponent
      remaining = leftOvers
    }

    components
  }
}
