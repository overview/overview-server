/*
 * nodewriter.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */

package com.overviewdocs.persistence

import scala.collection.mutable.Queue

import com.overviewdocs.clustering.DocTreeNode
import com.overviewdocs.models.{Node,NodeDocument,DocumentSetCreationJobNode}
import com.overviewdocs.models.tables.{Nodes,NodeDocuments,DocumentSetCreationJobNodes}

/**Writes out tree with the given root node to the database.
  *
  * Inserts entries into document and node_document tables. Documents contained
  * by the nodes must already exist in the database.
  *
  * If this clustering job is interrupted, the tree will remain in the
  * database.
  */
class NodeWriter(jobId: Long, treeId: Long) {
  val rootNodeId = new NodeIdGenerator(treeId).rootId

  def write(root: DocTreeNode): Unit = {
    writeNodes(root)
    writeNodeDocuments(root)
  }

  private def writeNodes(root: DocTreeNode): Unit = {
    val inserter = new BatchInserter[Node](1000, Nodes)

    // levelorder() from https://en.wikipedia.org/wiki/Tree_traversal
    val queue: Queue[(Option[Long],DocTreeNode)] = Queue((None,root))
    var nodeId = rootNodeId

    while (!queue.isEmpty) {
      val (parentId, node) = queue.dequeue

      inserter.insert(Node(nodeId, rootNodeId, parentId, node.description, node.docs.size, node.children.isEmpty))
      node.orderedChildren.foreach { subNode => queue.enqueue((Some(nodeId), subNode)) }
      nodeId += 1
    }

    inserter.flush
  }

  private def writeNodeDocuments(root: DocTreeNode): Unit = {
    val inserter = new BatchInserter(1000, NodeDocuments)

    // levelorder() from https://en.wikipedia.org/wiki/Tree_traversal
    val queue: Queue[DocTreeNode] = Queue(root)
    var nodeId = rootNodeId

    while (!queue.isEmpty) {
      val node = queue.dequeue

      node.docs.toArray.sorted.foreach { documentId =>
        inserter.insert(NodeDocument(nodeId, documentId))
      }

      node.orderedChildren.foreach { node => queue.enqueue(node) }

      nodeId += 1
    }

    inserter.flush
  }
}
