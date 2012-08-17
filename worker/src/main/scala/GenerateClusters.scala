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
import scala.collection.mutable.{Set,Stack}
import clustering.ClusterTypes._

object ConnectedComponents {

  // Takes a node, and a set of unvisited nodes, and yields all nodes we an visit next
  type EdgeEnumerationFn[T] = (T, Set[T]) => Iterable[T]
  
  // Returns component containing startNode, plus all nodes not in component
  def SingleComponent[T](startNode : T, allNodes : Set[T], edgeEnumerator : EdgeEnumerationFn[T] ) : (Set[T], Set[T]) = {
    var component = Set[T](startNode)         // all nodes found to be in the component so far 
    val frontier = Stack[T](startNode)        // nodes in the component that we have not checked the edges of
    var remaining = allNodes - startNode      // nodes not yet visited
    
    // walk outward from each node in the frontier, until the frontier is empty or we run out of nodes
    while (!frontier.isEmpty && !remaining.isEmpty) {
      val a = frontier.pop
      
      for (b <- edgeEnumerator(a, remaining)) {        // for every remaining we can reach from a...
        component += b
        frontier.push(b)
        remaining -=b
      }
    }
    
    (component, remaining)
  }

  // Produce all connected components 
  def AllComponents[T](allNodes : Set[T], edgeEnumerator : EdgeEnumerationFn[T] ) : Set[Set[T]] = {
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
  def CosineDistance(a: DocumentVector, b: DocumentVector) = {
    1.0 - SparseDot(a,b)
  }
}


// Document tree node. Contains items, children, and a description which lists top terms in all docs of this node
class DocTreeNode(val docs : Set[DocumentID]) {
  var description = ""
  var children:Set[DocTreeNode] = Set[DocTreeNode]()
 
  // return children in predictable order. Presently, sort by descending by size, and then ascending by document IDs
  def OrderedChildren : List[DocTreeNode] = {
    children.toList.sortWith((a,b) => (a.docs.size > b.docs.size) || (a.docs.size == b.docs.size && a.docs.min < b.docs.min))
  }
 
  // simple string representation, good for unit tests. We sort the sets to ensure consistent output
  override def toString = {
    "(" + docs.toList.sorted.mkString(",") + 
       (if (!children.isEmpty) 
          ", " + OrderedChildren.mkString(", ") 
        else 
          "") + 
    ")"
  }
  
  //  Tree pretty printer
  private def prettyString2(indent : Int = 0) : String = {
    " " * indent + docs.toList.sorted.mkString(",") + 
      (if (!children.isEmpty) 
        "\n" + OrderedChildren.map(_.prettyString2(indent+4)).mkString("\n")
      else
        "")
  }
  def prettyString = prettyString2(0)
}


class DocTreeBuilder(val docVecs : DocumentSetVectors, val distanceFn : (DocumentVector, DocumentVector) => Double) {

  // Produces all docs reachable from a given start doc, given thresh
  // Unoptimized implementation, scans through all possible edges (N^2 total)
  def reachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs:Set[DocumentID]) : Iterable[DocumentID] = {
    for (otherDoc <- otherDocs; if distanceFn(docVecs(thisDoc), docVecs(otherDoc)) <= thresh)
        yield otherDoc
  }

  // Expand out the nodes of the tree by thresholding the documents in each and seeing if they split into components
  // Returns new set of leaf nodes
  private def ExpandTree(currentLeaves : List[DocTreeNode], thresh:Double) =  {
     var nextLeaves = List[DocTreeNode]()

     for (node <- currentLeaves) {

       val childComponents = ConnectedComponents.AllComponents[DocumentID](node.docs, reachableDocs(thresh, _, _))
       
       if (childComponents.size == 1) {
         // lower threshold did not split this component, pass unchanged to next level
         nextLeaves = node :: nextLeaves
       } else {
         // lower threshold did split this component, create a child TreeNode for each resulting component
         for (component <- childComponents) {
           val newLeaf= new DocTreeNode(component)
           node.children += newLeaf
           nextLeaves =  newLeaf:: nextLeaves
         }
       }       
     }
     
     nextLeaves    
  }

  // Steps distance thresh along given sequence. First step must always be 1 = full graph, 0 must always be last = leaves
  def BuildTree(threshSteps: Seq[Double]) : DocTreeNode = {
    require(threshSteps.head == 1.0)
    require(threshSteps.last == 0.0)
    require(threshSteps.forall(step => step >= 0 && step <= 1.0))
        
    // root thresh=1.0 is one node with all documents
    var topLevel = Set(docVecs.keys.toArray:_*)
    val root = new DocTreeNode(topLevel)
          
    // intermediate levels created by successively thresholding all edges, (possibly) breaking each component apart
    var currentLeaves = List(root)  
    val intermediateSteps = threshSteps.drop(1).dropRight(1)    // remove first 1.0 and last 0.0 val
    for (thresh <- intermediateSteps) {
      currentLeaves = ExpandTree(currentLeaves, thresh)
    }
       
    // bottom level thresh=0.0 is one leaf node for each document
    for (node <- currentLeaves) {
      if (node.docs.size > 1)                                   // don't expand if already one node
        node.children = node.docs.map( item=> new DocTreeNode(Set(item)) )
    }
         
    root
  }
}

// Helpfully encapsulate the document tree construction with useful defaults
object BuildDocTree {
  def apply(docVecs : DocumentSetVectors) 
        : DocTreeNode = {
    
    // By default: cosine distance, and step down in 0.1 increments 
    val distanceFn = DistanceFn.CosineDistance _
    val threshSteps = List(1, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0) // can't do (1.0 to 0.1 by -0.1) cause last val must be exactly 0
    
    new DocTreeBuilder(docVecs, distanceFn).BuildTree(threshSteps)
  }
}
