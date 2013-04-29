package org.overviewproject.persistence

import scala.collection.mutable.Seq
import org.squeryl.Table



/**
 * Batches the insertion of nodeIds and documentIds into the node_document table.
 * Once the number of insertions reaches the threshold, the batch insert is executed.
 * flush() must be called to ensure that all inserts get executed.
 */
class BatchInserter[T](threshold: Long, table: Table[T]) {
  var insertCount: Long = _
  var currentBatch: Seq[T] = Seq()

  resetBatch

  /** queue up item for insertion in the node_document table */
  def insert(item: T){
    currentBatch +:= item
    
    insertCount += 1

    if (insertCount == threshold) {
      flush
    }
  }

  /** execute the batch insert */
  def flush {
    table.insert(currentBatch)
    resetBatch
  }

  private def resetBatch {
    insertCount = 0
    currentBatch = Seq.empty
  }
}