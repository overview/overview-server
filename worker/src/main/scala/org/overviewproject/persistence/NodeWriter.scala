/*
 * nodewriter.scala
 *
 * overview project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import java.sql.Connection
import org.overviewproject.clustering.DocTreeNode
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.tree.orm.{ Node, NodeDocument }


/**
 * Writes out tree with the given root node to the database.
 * Inserts entries into document and node_document tables. Documents contained by
 * the nodes must already exist in the database.
 */
class NodeWriter(documentSetId: Long, treeId: Long) {
  val batchInserter = new BatchInserter[NodeDocument](500, Schema.nodeDocuments)
  val ids = new DocumentSetIdGenerator(documentSetId)
  
  def write(root: DocTreeNode)(implicit c: Connection) {
    writeSubTree(root, None)
    batchInserter.flush
  }

  private def writeSubTree(node: DocTreeNode, parentId: Option[Long])(implicit c: Connection) {
    val n = Node(
      id=ids.next,
      treeId = treeId,
      documentSetId=documentSetId,
      parentId=parentId,
      description=node.description,
      cachedSize=node.documentIdCache.numberOfDocuments,
      cachedDocumentIds=node.documentIdCache.documentIds,
      isLeaf = node.children.isEmpty
    )

    Schema.nodes.insert(n)

    node.docs.foreach(docId => batchInserter.insert(NodeDocument(n.id, docId)))

    node.children.foreach(writeSubTree(_, Some(n.id)))
  }
}
