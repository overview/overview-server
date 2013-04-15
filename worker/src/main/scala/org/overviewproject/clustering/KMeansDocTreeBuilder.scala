/**
 * KMeansDocTreeBuilder.scala
 * Build a document tree using iterative k-means algorithm (variable k)
 * 
 * Overview Project, created March 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress, NoProgressReporting }
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.IterativeKMeansDocuments

// Take a node and create K children.
// Encapsulates parameters of our our-means clustering
class KMeansNodeSplitter(protected val docVecs: DocumentSetVectors, protected val k:Int) {
  private val km = new IterativeKMeansDocuments(docVecs)
  
  def splitNode(node:DocTreeNode) : Unit = {  
    val stableDocs = node.docs.toArray.sorted   // sort documentIDs, to ensure consistent input to kmeans
    val assignments = km(stableDocs, k)
    for (i <- 0 until k) { 
      val docsInThisCluster = km.elementsInCluster(i, stableDocs, assignments)  // document IDs assigned to cluster i, lazily produced
      if (docsInThisCluster.size > 0)
        node.children += new DocTreeNode(Set(docsInThisCluster:_*))
    }
    
    if (node.children.size == 1)    // if all docs went into single node, make this a leaf, we are done
      node.children.clear           // (either "really" only one cluster, or clustering alg problem, but let's never infinite loop)
  }
  
  def makeALeafForEachDoc(node:DocTreeNode) = {
    if (node.docs.size > 1)
      node.docs foreach { id => 
        node.children += new DocTreeNode(Set(id))
    }
  }
}


class KMeansDocTreeBuilder(_docVecs: DocumentSetVectors, _k:Int) 
  extends KMeansNodeSplitter(_docVecs, _k) {

  val stopSize = 16   // keep breaking into clusters until <= 16 docs in a node
  val maxDepth = 10   // ...or we reach depth limit
  
  private def splitNode(node:DocTreeNode, level:Integer, progAbort:ProgressAbortFn) : Unit = {
    
    if (!progAbort(Progress(0, ClusteringLevel(1)))) { // if we haven't been cancelled...
  
      require(node.docs.size > 0)
      
      if ((node.docs.size > stopSize) && (level < maxDepth)) {
         
        splitNode(node)
        
        if (node.children.isEmpty) {
          // we couldn't split it, just put each doc in a leaf
          makeALeafForEachDoc(node)
        } else {
          // recurse, computing progress along the way
          var i=0
          var denom = node.children.size.toDouble
          node.children foreach { node =>
            splitNode(node, level+1, makeNestedProgress(progAbort, i/denom, (i+1)/denom))
            i+=1
          }
        }
      } else {
        // smaller nodes, or max depth reached, produce a leaf for each doc
        if (node.docs.size > 1) 
          node.children = node.docs.map(item => new DocTreeNode(Set(item)))
      }
      
      progAbort(Progress(1, ClusteringLevel(1)))
    }
  }
  
  def BuildTree(root:DocTreeNode, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    splitNode(root, 1, progAbort)   // root is level 0, so first split is level 1
    
    root
  }
}
