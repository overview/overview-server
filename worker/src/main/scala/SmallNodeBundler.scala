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

package overview.clustering

import org.overviewproject.clustering.ClusterTypes._
import scala.collection.mutable.Set

object SmallNodeBundler {
  
  def apply(root:DocTreeNode, desiredMaxChildren:Int) : Unit = limitTreeMaxChildren(root, desiredMaxChildren)
  
  
  def limitNodeMaxChildren(n:DocTreeNode, maxChildren:Int) : Unit = {
    require(maxChildren >= 2)
  
    var kidsRemaining = n.children.size

    if (kidsRemaining > maxChildren) {
      kidsRemaining += 1                              // we create a new node to put the bundled kids under
      
      var kids = n.orderedChildren.reverse            //  this list goes from smallest to biggest
      var bundledNodes = Set[DocTreeNode]()
      var bundledDocs = Set[DocumentID]()
      
      while (kidsRemaining > maxChildren) {           // each iteration bundles a kid, until only maxChildren left
        val eatKid = kids.head
        bundledNodes += eatKid
        bundledDocs ++= eatKid.docs
        kids = kids.tail
        kidsRemaining -= 1
      }

      var otherNode = new DocTreeNode(bundledDocs)    // create new node that will hold all the kids we bundled
      otherNode.children = bundledNodes
      otherNode.description = "(other)"
        
      n.children = Set(kids: _*) + otherNode
    } 
  }
  
  def limitTreeMaxChildren(n:DocTreeNode, maxChildren:Int) : Unit = {
    limitNodeMaxChildren(n, maxChildren)
    n.children foreach {
      limitTreeMaxChildren(_, maxChildren)
    }
  }
  
  
}