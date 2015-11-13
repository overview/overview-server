/**
 * GenerateClusters.scala
 * Given a set of document vectors, return a tree hierarchical tree of clusters
 * Mostly just setup and call into a DocTreeBuilder
 *
 * Overview, created July 2012
 *
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering

import scala.collection.mutable.Set
import com.overviewdocs.nlp.DocumentVectorTypes._
import com.overviewdocs.util.Configuration

// Given a set of document vectors, generate a tree of nodes and their descriptions
// This is where all of the hard-coded algorithmic constants live
object BuildDocTree {

  // Create two nodes: one with all empty docs (no terms), one with all the rest
  def gatherEmptyDocs(docVecs: DocumentSetVectors) : Tuple2[DocTreeNode, DocTreeNode] = {
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

  def applyKMeans(root:DocTreeNode, docVecs: DocumentSetVectors, onProgress: Double => Unit): DocTreeNode = {
    val arity = 5
    val builder = new KMeansDocTreeBuilder(docVecs, arity)
    builder.BuildTree(root, onProgress) // actually build the tree!
  }

  def applyKMeansComponents(root:DocTreeNode, docVecs: DocumentSetVectors, onProgress: Double => Unit): DocTreeNode = {
    val arity = 5
    val builder = new KMeansComponentsDocTreeBuilder(docVecs, arity)
    builder.BuildTree(root, onProgress) // actually build the tree!
  }

  def applyConnectedComponents(root:DocTreeNode, docVecs: DocumentSetVectors, onProgress: Double => Unit): DocTreeNode = {
    (new ConnectedComponentDocTreeBuilder(docVecs)).applyConnectedComponents(root, docVecs, onProgress)
  }

  def apply(docVecs: DocumentSetVectors, onProgress: Double => Unit): DocTreeNode = {
    var (nonEmptyDocs, emptyDocs) = gatherEmptyDocs(docVecs)

    Configuration.getString("clustering_alg") match {
      case "KMeans" => applyKMeans(nonEmptyDocs, docVecs, onProgress)
      case "ConnectedComponents" => applyConnectedComponents(nonEmptyDocs, docVecs, onProgress)
      case _  => applyKMeansComponents(nonEmptyDocs, docVecs, onProgress)
    }

    SuggestedTags.makeSuggestedTagsForTree(docVecs, nonEmptyDocs) // create a descriptive label for each node

    // If there are any empty documents, create a node labelled "no meaningful words"
    var tree = nonEmptyDocs
    if (emptyDocs.docs.size>0) {
      emptyDocs.description = ""

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
