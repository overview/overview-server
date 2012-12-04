/**
 * DocTreeNode.scala
 * In-memory representation for document tree during tree generation.
 * 
 * Overview Project, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.clustering.ClusterTypes._

// Document tree node. Contains items, children, and a description which lists top terms in all docs of this node
class DocTreeNode(val docs: Set[DocumentID]) {
  var description = ""
  var children: Set[DocTreeNode] = Set[DocTreeNode]()

  // return children in predictable order. Sort descending by size, then ascending by document IDs
  def orderedChildren: List[DocTreeNode] = {
    children.toList.sortWith((a, b) => (a.docs.size > b.docs.size) || (a.docs.size == b.docs.size && a.docs.min < b.docs.min))
  }

  // simple string representation, good for unit tests. We sort the sets to ensure consistent output
  override def toString = {
    "(" + docs.toList.sorted.mkString(",") +
      (if (!children.isEmpty)
        ", " + orderedChildren.mkString(", ")
      else
        "") +
      ")"
  }

  //  Tree pretty printer
  def prettyString(indent: Int = 0): String = {
    " " * indent + docs.toList.sorted.mkString(",") +
      " -- " + description +
      (if (!children.isEmpty)
        "\n" + orderedChildren.map(_.prettyString(indent + 4)).mkString("\n")
      else
        "")
  }
}