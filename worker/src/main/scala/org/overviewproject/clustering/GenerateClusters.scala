/**
 * GenerateClusters.scala
 * Given a set of document vectors, return a tree hierarchical tree of clusters
 * Mostly just setup and call into a DocTreeBuilder
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
  
  def applyKMeans(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    val arity = 5
    val builder = new KMeansDocTreeBuilder(docVecs, arity)
    builder.BuildTree(root, progAbort) // actually build the tree!
  }
  
  def applyKMeansComponents(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    val arity = 5
    val builder = new KMeansComponentsDocTreeBuilder(docVecs, arity)
    builder.BuildTree(root, progAbort) // actually build the tree!
  }
  
   
  def apply(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    var (nonEmptyDocs, emptyDocs) = gatherEmptyDocs(docVecs)
    
    applyKMeansComponents(nonEmptyDocs, docVecs, progAbort)    
        
    new TreeLabeler(docVecs).labelNode(nonEmptyDocs)    // create a descriptive label for each node
    //ThresholdTreeCleaner(nonEmptyDocs)                  // combine nodes that are too small
    
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
