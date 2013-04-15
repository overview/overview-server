/**
 * SmallNodeBundler.scala
 *
 * Take all but the largest child nodes and group them under a new node labeled "other", 
 * for every node in the tree. User specifies resulting maximum number of children per node (must be >= 2)
 * Might also be called the DustBuster (dust == single document nodes)
 * 
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.nlp.DocumentVectorTypes._


// Root tree cleaner class. Recurses over the tree, but does not recurse into bundled nodes
trait TreeCleaner {
  
  val bundledDescription = "(other)"

  def apply(root:DocTreeNode) = cleanTree(root)
  
  def cleanChildren(n:DocTreeNode) : Unit 
      
  def cleanTree(n:DocTreeNode) : Unit = {
    cleanChildren(n)
    n.children foreach { child =>
      if (child.description != bundledDescription)
        cleanTree(child)
    }
  } 
}


// A tree cleaner that bundles all nodes smaller than a certain size.
object ThresholdTreeCleaner extends TreeCleaner {
  
  def cleanChildren(n:DocTreeNode) : Unit = {
    val minNodesForBundle = 3
   
    val (bigEnoughNodes, tooSmallNodes) = n.children.partition(showThisNode(_, n.docs.size))
    
    if (tooSmallNodes.size >= minNodesForBundle) {

      // create new node that will hold all the children we're bundling
      val bundledDocs = tooSmallNodes.flatMap(_.docs)
      var bundledNode = new DocTreeNode(bundledDocs)   
      bundledNode.children = tooSmallNodes
      bundledNode.description = bundledDescription
     
      n.children = bigEnoughNodes + bundledNode
    }
  }
  
  // keep a node out of  "other" if it has at least 8 docs, or 1/64 of parent size (so small nodes don't get "other" children)
  private def showThisNode(n:DocTreeNode, parentSize:Int) : Boolean = {
      val showFixed = 8
      val showFraction = 64
    
      n.docs.size >= math.min(showFixed, parentSize / showFraction)
  }
}

// A tree cleaner that simply packages all children of each node except desiredMaxChildren-1 into a bundled node
class SmallNodeBundler(val desiredMaxChildren:Int) extends TreeCleaner {

  def cleanChildren(n:DocTreeNode) : Unit = {
    require(desiredMaxChildren >= 2)
  
    var kidsRemaining = n.children.size

    if (kidsRemaining > desiredMaxChildren) {
      kidsRemaining += 1                              // we create a new node to put the bundled kids under
      
      var kids = n.orderedChildren.reverse            //  this list goes from smallest to biggest
      var bundledNodes = Set[DocTreeNode]()
      var bundledDocs = Set[DocumentID]()
      
      while (kidsRemaining > desiredMaxChildren) {    // each iteration bundles a kid, until only maxChildren left
        val eatKid = kids.head
        bundledNodes += eatKid
        bundledDocs ++= eatKid.docs
        kids = kids.tail
        kidsRemaining -= 1
      }

      var otherNode = new DocTreeNode(bundledDocs)    // create new node that will hold all the kids we bundled
      otherNode.children = bundledNodes
      otherNode.description = bundledDescription
        
      n.children = Set(kids: _*) + otherNode
    } 
  }
}