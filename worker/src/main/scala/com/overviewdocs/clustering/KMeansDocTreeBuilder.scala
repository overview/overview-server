/**
 * KMeansDocTreeBuilder.scala
 * Build a document tree using iterative k-means algorithm (variable k)
 *
 * Overview, created March 2012
 *
 * @author Jonathan Stray
 */

package com.overviewdocs.clustering

import scala.collection.mutable.Set
import com.overviewdocs.nlp.DocumentVectorTypes._
import com.overviewdocs.nlp.IterativeKMeansDocuments

// Take a node and create K children.
// Encapsulates parameters of our our-means clustering
class KMeansNodeSplitter(protected val docVecs: DocumentSetVectors, protected val k:Int) {
  protected def makeNestedProgress(onProgress: Double => Unit, start: Double, end: Double): Double => Unit = {
    (inner) => onProgress(start + inner * (end - start))
  }

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
}


class KMeansDocTreeBuilder(_docVecs: DocumentSetVectors, _k: Int) extends KMeansNodeSplitter(_docVecs, _k) {

  val stopSize = 16   // keep breaking into clusters until <= 16 docs in a node
  val maxDepth = 10   // ...or we reach depth limit

  private def splitNode(node:DocTreeNode, level:Integer, onProgress: Double => Unit) : Unit = {
    require(node.docs.size > 0)

    onProgress(0)

    if ((node.docs.size > stopSize) && (level < maxDepth)) {

      splitNode(node)

      if (!node.children.isEmpty) {
        // recurse, computing progress along the way
        var i=0
        var denom = node.children.size.toDouble
        node.children foreach { node =>
          splitNode(node, level+1, makeNestedProgress(onProgress, i/denom, (i+1)/denom))
          i+=1
        }
      }
    }

    onProgress(1)
  }

  def BuildTree(root:DocTreeNode, onProgress: Double => Unit): DocTreeNode = {
    splitNode(root, 1, onProgress)   // root is level 0, so first split is level 1

    root
  }
}
