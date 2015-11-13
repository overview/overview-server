package com.overviewdocs.persistence

import scala.collection.mutable

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.{Node,NodeDocument}
import com.overviewdocs.models.tables.{Nodes,NodeDocuments}

/** Writes out the given tree nodes, batched.
  *
  * Inserts entries into the `node` and `node_document` tables. Documents must
  * exist in the database, or these methods will crash.
  *
  * The caller must provide nodes in order: that is, a parent before its
  * children.
  *
  * Not thread-safe.
  */
class NodeWriter extends HasBlockingDatabase {
  private val BatchSize = 5000 // Big -> jitter; small -> everything is slow
  private val nodeBatch = mutable.ArrayBuffer[Node]()
  private val nodeDocumentBatch = mutable.ArrayBuffer[NodeDocument]()

  def blockingCreateAndFlushIfNeeded(
    id: Long,
    rootId: Long,
    parentId: Option[Long],
    description: String,
    isLeaf: Boolean,
    documentIds: Seq[Long]
  ): Unit = {
    nodeBatch.+=(Node(id, rootId, parentId, description, documentIds.length, isLeaf))
    nodeDocumentBatch.++=(documentIds.map(documentId => NodeDocument(id, documentId)))

    if (nodeDocumentBatch.size > BatchSize) {
      blockingFlush
    }
  }

  def blockingFlush: Unit = {
    import database.api._

    blockingDatabase.runUnit(Nodes.++=(nodeBatch))
    blockingDatabase.runUnit(NodeDocuments.++=(nodeDocumentBatch))

    nodeBatch.clear
    nodeDocumentBatch.clear
  }
}
