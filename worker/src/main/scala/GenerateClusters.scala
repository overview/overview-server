/**
 * GenerateClusters.scala
 * Given a set of document vectors, return a tree hierarchical tree of clusters
 * Based on an algorithm by Stephen Ingram
 * 
 * Overview Project, created July 2012
 * 
 * @author Jonathan Stray
 * 
 */

package clustering

import models._
import scala.collection.mutable
import clustering.ClusterTypes._


// Encapsulates document-document distance function. Returns in range 0 == identical to 1 == unrelated
object DistanceFn {
  // sparse dot product on two term->float maps
  private def SparseDotCore(a: DocumentVector, b: DocumentVector) : Double= {
      a.foldLeft(0.0) { case (sum, (term, weight)) => sum + b.getOrElse(term,0f).toDouble * weight.toDouble }  
  }
  
  // fold by scanning the the shorter list, for efficiency
  private def SparseDot(a: DocumentVector, b: DocumentVector) = {
    if (a.size < b.size) 
      SparseDotCore(a,b) 
    else 
      SparseDotCore(b,a)  
  }
  
  // Document distance computation. Returns 1 - similarity, where similarity is cosine of normalized vectors
  def distance(a: DocumentVector, b: DocumentVector) = {
    1.0 - SparseDot(a,b)
  }
}

case class TreeNode(documents : Set[DocumentID], description: String)

class TreeBuilder(val docVecs : DocumentSetVectors) {

  type nodeSet = Set[DocumentID]
  
  // Returns component containing startDoc, plus all nodes not in component
  // At the moment, assumes an edge between every two nodes (fully connected)
  def GetSingleConnectedComponent(startDoc: DocumentID, graph:nodeSet, thresh:Double) : (nodeSet, nodeSet) = {
    // Mutable algorithm tracks three sets: 
    //   component - all nodes found to be in the component so far 
    //   frontier - nodes in the component that we have not checked the edges of
    //   remaining - nodes not yet visited
    // We walk outward from each node in the frontier, until the frontier is empty
    
    var component = Set[DocumentID](startDoc)
    var frontier = List(startDoc)
    var remaining = graph - startDoc
    
    while (!frontier.isEmpty) {
      val a = frontier.head         // pop first node from frontier
      frontier = frontier.tail
      
      for (b <- remaining) {        // for every other remaining node, if there is an edge to it...
        if (DistanceFn.distance(docVecs(a), docVecs(b)) < thresh) {   
          component += b
          frontier = b :: frontier
          remaining -=b
        }
      }
    }
    
    (component, remaining)
  }

  // Produce all connected components 
  def GetConnectedComponents(graph:nodeSet, thresh:Double) : Set[nodeSet] = {
    var components = Set[nodeSet]()
    var remaining = graph
    
    while (!remaining.isEmpty) {
      val (newComponent, leftOvers) = GetSingleConnectedComponent(remaining.first, remaining, thresh)
      components += newComponent
      remaining = leftOvers
    }
    
    components
  }
  
  
  def BuildTree(threshStep: Double) : TreeNode = {
     docVecs.keys.toSet
    
  }

}
