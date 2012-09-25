/*
 * NodeDocumentBatchInserter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import java.sql.Connection

/**
 * Batches the insertion of nodeIds and documentIds into the node_document table.
 * Once the number of insertions reaches the threshold, the batch insert is executed.
 * flush() must be called to ensure that all inserts get executed.
 */
class NodeDocumentBatchInserter(threshold: Long) {
  var insertCount: Long = _
  var batchQuery: BatchSql = createBatchQuery

  resetBatch

  /** queue up nodeId and documentId for insertion in the node_document table */
  def insert(nodeId: Long, documentId: Long)(implicit c: Connection) {
    batchQuery = batchQuery.addBatchParams(nodeId, documentId)
    insertCount += 1

    if (insertCount == threshold) {
      flush
    }
  }

  /** execute the batch insert */
  def flush(implicit c: Connection) {
    val result = batchQuery.execute

    resetBatch
  }

  private def resetBatch {
    insertCount = 0
    batchQuery = createBatchQuery
  }

  private def createBatchQuery: BatchSql =
    SQL("""
        INSERT INTO node_document (node_id, document_id)
        VALUES ({nodeDocument}, {documentId})
        """).asBatch

}
