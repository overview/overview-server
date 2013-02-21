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

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress, NoProgressReporting }
import org.overviewproject.clustering.ClusterTypes._


class KMeansDocTreeBuilder(_docVecs: DocumentSetVectors, _k:Int) 
  extends KMeansNodeSplitter(_docVecs, _k) {

  val stopSize = 16   // keep breaking into clusters until <= 16 docs in a node
  
  private def splitNode(node:DocTreeNode, progAbort:ProgressAbortFn) : Unit = {  
    if (!progAbort(Progress(0, ClusteringLevel(1)))) { // if we haven't been cancelled...
  
      require(node.docs.size > 0)
      
      if (node.docs.size > stopSize) {
         
        splitNode(node)
        
        if (node.children.isEmpty) {
          // we couldn't split it, just put each doc in a leaf
          makeALeafForEachDoc(node)
        } else {
          // recurse, computing progress along the way
          var i=0
          var denom = node.children.size.toDouble
          node.children foreach { node =>
            splitNode(node, makeNestedProgress(progAbort, i/denom, (i+1)/denom))
            i+=1
          }
        }
      } else {
        // smaller nodes, produce a leaf for each doc
        if (node.docs.size > 1) 
          node.children = node.docs.map(item => new DocTreeNode(Set(item)))
      }
      
      progAbort(Progress(1, ClusteringLevel(1)))
    }
  }
  
  def BuildTree(root:DocTreeNode, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    splitNode(root, progAbort)
    
    root
  }
}


// Given a set of document vectors, generate a tree of nodes and their descriptions
// This is where all of the hard-coded algorithmic constants live
object BuildDocTree {

  // Create two nodes: one with all empty docs (no terms), one with all the rest
  def gatherEmptyDocs(docVecs: DocumentSetVectors) : Pair[DocTreeNode, DocTreeNode] = {
    val nonEmptyDocs = Set[DocumentID]()
    val emptyDocs = Set[DocumentID]()
    docVecs foreach { case (id,vector) =>   // yes, I could use partition, but I only need the keys, and docVecs is potentially huge
      if (vector.length > 0)
        nonEmptyDocs += id
      else
        emptyDocs += id
    }
    (new DocTreeNode(nonEmptyDocs), new DocTreeNode(emptyDocs))
  }
  
  def applyConnectedComponents(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    // By default: step down in roughly 0.1 increments
    val threshSteps = List(1, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0)

    // Use edge sampling if docset is large enough, with hard-coded number of samples
    // Random graph connectivity arguments suggest num samples does not need to scale with docset size
    val numDocsWhereSamplingHelpful = 10000
    val numSampledEdgesPerDoc = 200

    val builder = new ConnectedComponentDocTreeBuilder(docVecs)
    if (docVecs.size > numDocsWhereSamplingHelpful)
      builder.sampleCloseEdges(numSampledEdgesPerDoc) // use sampled edges if the docset is large
    val tree = builder.BuildTree(root, threshSteps, progAbort) // actually build the tree!

    tree
  }

  def applyKMeans(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    val arity = 5
    val builder = new KMeansDocTreeBuilder(docVecs, arity)
    builder.BuildTree(root, progAbort) // actually build the tree!
  }

  def apply(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    var (nonEmptyDocs, emptyDocs) = gatherEmptyDocs(docVecs)
    
    applyKMeans(nonEmptyDocs, docVecs, progAbort)    
        
    new TreeLabeler(docVecs).labelNode(nonEmptyDocs)    // create a descriptive label for each node
    ThresholdTreeCleaner(nonEmptyDocs)                  // combine nodes that are too small
    
    // If there are any empty documents, create a new root with all documents
    // Add children of nonEmptyDocs, plus node containing emptyDocs
    var tree = nonEmptyDocs
    if (emptyDocs.docs.size>0) {
      tree = new DocTreeNode(Set(docVecs.keys.toSeq:_*))  // all docs
      tree.description = nonEmptyDocs.description
      tree.children ++= nonEmptyDocs.children
      emptyDocs.description = "(no meaningful words)"
      tree.children += emptyDocs
    }

    DocumentIdCacheGenerator.createCache(tree)
    
    tree
  }
 
}
