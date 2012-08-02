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

class ConnectedComponents[T](val reachableNodes : (T, Set[T]) => Iterable[T]) {
    
  // Returns component containing startNode, plus all nodes not in component
  def GetSingleConnectedComponent(startNode: T, allNodes:Set[T]) : (Set[T], Set[T]) = {
    var component = Set[T](startNode)             // all nodes found to be in the component so far 
    val frontier = mutable.Stack[T](startNode)    // nodes in the component that we have not checked the edges of
    var remaining = allNodes - startNode          // nodes not yet visited
    
    // walk outward from each node in the frontier, until the frontier is empty (or we run out of nodes)
    while (!frontier.isEmpty && !remaining.isEmpty) {
      val a = frontier.pop
      
      for (b <- reachableNodes(a, remaining)) {        // for every remaining we can reach from a...
        component += b
        frontier.push(b)
        remaining -=b
      }
    }
    
    (component, remaining)
  }

  // Produce all connected components 
  def GetConnectedComponents(allNodes:Set[T]) : Set[Set[T]] = {
    var components = Set[Set[T]]()
    var remaining = allNodes
    
    while (!remaining.isEmpty) {
      val (newComponent, leftOvers) = GetSingleConnectedComponent(remaining.first, remaining)
      components += newComponent
      remaining = leftOvers
    }
    
    components
  }
}

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


class TreeNode(var docs : Set[DocumentID], var description : String, var children:Set[TreeNode] = Set[TreeNode]()) 

class TreeBuilder(val docVecs : DocumentSetVectors, val distanceFn : (DocumentVector, DocumentVector) => Double) {

  type nodeSet = Set[DocumentID]
  
  // Produces a descriptive label for a set of documents, by taking top N keywords
  def DocsetDescription(documents: Set[DocumentID], N : Int) : String = {
    "words, are, awesome"
  }
  
  // Produces all docs reachable from a given start doc, given thresh
  // Unoptimized implementation, scans through all possible edges (N^2 total)
  def reachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs:Set[DocumentID]) : Iterable[DocumentID] = {
    for (otherDoc <- otherDocs; if distanceFn(docVecs(thisDoc), docVecs(otherDoc)) <= thresh)
        yield otherDoc
  }

  // Steps distance thresh from 1.0 to 0 in increments. 1 is always full graph, 0 is always leaves
  def BuildTree(threshStep: Double) : TreeNode = {
     var topLevel : nodeSet = docVecs.keys.toSet
     val numDescTerms = 20 // take top 20 terms
     
     // root is all documents
     val root = new TreeNode(topLevel, DocsetDescription(topLevel, numDescTerms))
     
     // intermediate levels created by thresholding all edges watching how connected components break apart
     var currentLevel = List(root)
     val steps = (1.0-threshStep to threshStep/2 by threshStep) // don't go all the way down to 0
     
     for (thresh <- steps) {
       var nextLevel = List[TreeNode]()
       for (node <- currentLevel) {
         
         //val componentFinder = new ConnectedComponents((doc:DocumentID, otherDocs:Set[DocumentID]) => reachableDocs(thresh, doc, otherDocs))
         
         val componentFinder = new ConnectedComponents[DocumentID](reachableDocs(thresh, _, _))
         val childComponents = componentFinder.GetConnectedComponents(node.docs)
         
         if (childComponents.size == 1) {
           // lower threshold did not split this component, pass unchanged to next level
           nextLevel = node :: nextLevel               
         } else {
           // lower threshold did split this component, create a child TreeNode for each resulting component
           for (component <- childComponents) {
             val newChild = new TreeNode(component, DocsetDescription(component, numDescTerms))
             node.children += newChild
             nextLevel =  newChild :: nextLevel
           }
         }
         
         currentLevel = nextLevel
       }
     }
       
    // bottom level is one leaf node for each document
    for (node <- currentLevel) {
      node.children = node.docs.map( docID => new TreeNode(Set(docID), DocsetDescription(Set(docID), numDescTerms)) )
    }
         
    root
  }

}
