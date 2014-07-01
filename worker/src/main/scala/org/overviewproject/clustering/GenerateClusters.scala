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
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.util.Configuration

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

  def applyConnectedComponents(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    (new ConnectedComponentDocTreeBuilder(docVecs)).applyConnectedComponents(root, docVecs, progAbort)
  }

  def apply(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    var (nonEmptyDocs, emptyDocs) = gatherEmptyDocs(docVecs)

    Configuration.getString("clustering_alg") match {
      case "KMeans" => applyKMeans(nonEmptyDocs, docVecs, progAbort)
      case "ConnectedComponents" => applyConnectedComponents(nonEmptyDocs, docVecs, progAbort)
      case _  => applyKMeansComponents(nonEmptyDocs, docVecs, progAbort)
    }

    SuggestedTags.makeSuggestedTagsForTree(docVecs, nonEmptyDocs) // create a descriptive label for each node

    // If there are any empty documents, create a node labelled "no meaningful words"
    var tree = nonEmptyDocs
    if (emptyDocs.docs.size>0) {
      emptyDocs.description = "(no meaningful words)"

      if (nonEmptyDocs.docs.isEmpty) {
        // The tree is ONLY empty docs, so just one node
        tree = emptyDocs
      } else {
        // There are empty docs and non-empty docs. Make a new root, add both empty and non-empty to it.
        tree = new DocTreeNode(Set(docVecs.keys.toSeq:_*))  // all docs
        tree.description = nonEmptyDocs.description

        // If nonEmptyDocs is the root of a tree, add its children to our new root with all docs
        // Otherwise nonEmptyDocs is only a single node, so just add that node directly. Fixes #67871530
        if (nonEmptyDocs.children.size > 0)
          tree.children = nonEmptyDocs.children
        else
          tree.children = Set(nonEmptyDocs)

        tree.children += emptyDocs
      }
    }

    tree
  }
}
