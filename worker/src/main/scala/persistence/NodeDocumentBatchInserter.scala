/*
 * NodeDocumentBatchInserter.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import scala.collection.mutable.Seq

/**
 * Batches the insertion of nodeIds and documentIds into the node_document table.
 * Once the number of insertions reaches the threshold, the batch insert is executed.
 * flush() must be called to ensure that all inserts get executed.
 */
class NodeDocumentBatchInserter(threshold: Long) {
  var insertCount: Long = _
  var currentBatch: Seq[NodeDocument] = Seq()

  resetBatch

  /** queue up nodeId and documentId for insertion in the node_document table */
  def insert(nodeId: Long, documentId: Long){
    currentBatch +:= NodeDocument(nodeId, documentId)
    
    insertCount += 1

    if (insertCount == threshold) {
      flush
    }
  }

  /** execute the batch insert */
  def flush {
    import persistence.Schema.nodeDocuments
    nodeDocuments.insert(currentBatch)

    resetBatch
  }

  private def resetBatch {
    insertCount = 0
    currentBatch = Seq.empty
  }
}
