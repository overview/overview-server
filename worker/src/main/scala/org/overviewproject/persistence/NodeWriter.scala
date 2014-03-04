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
import org.overviewproject.persistence.orm.stores.TreeStore
import org.overviewproject.tree.orm.{ DocumentSetCreationJobTree, Node, NodeDocument, Tree }
import org.overviewproject.tree.orm.stores.BaseStore


/**
 * Writes out tree with the given root node to the database.
 * Inserts entries into document and node_document tables. Documents contained by
 * the nodes must already exist in the database.
 */
class NodeWriter(jobId: Long, tree: Tree) {
  val batchInserter = new BatchInserter[NodeDocument](500, Schema.nodeDocuments)
  val ids = new NodeIdGenerator(tree.id)

  
  def write(root: DocTreeNode)(implicit c: Connection) {
    TreeStore.insert(tree)
    insertJobTreeEntry
    writeSubTree(root, None)
    batchInserter.flush
  }

  private def insertJobTreeEntry: Unit = {
    val jobTree = DocumentSetCreationJobTree(jobId, tree.id)
    Schema.documentSetCreationJobTrees.insert(jobTree)
  }
    
  private def writeSubTree(node: DocTreeNode, parentId: Option[Long])(implicit c: Connection) {
    val n = Node(
      id=ids.next,
      treeId = tree.id,
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
