/*
 * NodeWriter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import anorm._
import org.overviewproject.clustering.DocTreeNode
import java.sql.Connection
import org.overviewproject.tree.orm.Node

/**
 * Writes out tree with the given root node to the database.
 * Inserts entries into document and node_document tables. Documents contained by
 * the nodes must already exist in the database.
 */
class NodeWriter(documentSetId: Long) {
  val batchInserter = new NodeDocumentBatchInserter(500)

  def write(root: DocTreeNode)(implicit c: Connection) {
    writeSubTree(root, None)
    batchInserter.flush
  }

  private def writeSubTree(node: DocTreeNode, parentId: Option[Long])(implicit c: Connection) {
    val n = Node(documentSetId, parentId, node.description,
      node.documentIdCache.numberOfDocuments, node.documentIdCache.documentIds)

    Schema.nodes.insert(n)

    node.docs.foreach(batchInserter.insert(n.id, _))

    node.children.foreach(writeSubTree(_, Some(n.id)))
  }

}
