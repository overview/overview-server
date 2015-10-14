/**
 * DocTreeNode.scala
 * In-memory representation for document tree during tree generation.
 *
 * Overview, created November 2012
 *
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering

import scala.collection.mutable.Set
import com.overviewdocs.nlp.DocumentVectorTypes.DocumentID

// Document tree node. Contains items, children, and a description which lists top terms in all docs of this node
class DocTreeNode(val docs: Set[DocumentID]) {
  var description: String = ""
  var children: Set[DocTreeNode] = Set[DocTreeNode]()

  var components = Set[DocumentComponent]() // used during clustering by KMeansComponentDocTreeBuilder, cleared after

  // return children in predictable order. Sort descending by size, then ascending by document IDs
  def orderedChildren: List[DocTreeNode] = {
    children.toList.sortWith { (a, b) =>
      if (a.docs.size > b.docs.size) {
        true
      } else if (a.docs.size == b.docs.size) {
        if (a.docs.isEmpty) {
          // Unit tests sometimes have no documents
          a.description < b.description
        } else {
          a.docs.min < b.docs.min
        }
      } else {
        false
      }
    }
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
