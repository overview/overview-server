package com.overviewdocs.persistence

import scala.collection.mutable.Buffer
import slick.lifted.Query

import com.overviewdocs.database.HasBlockingDatabase

/**
 * Batches the insertion of nodeIds and documentIds into the node_document table.
 * Once the number of insertions reaches the threshold, the batch insert is executed.
 * flush() must be called to ensure that all inserts get executed.
 */
class BatchInserter[T](threshold: Long, inserter: Query[_, T, Seq]) extends HasBlockingDatabase
{
  val currentBatch: Buffer[T] = Buffer()

  /** queue up item for insertion */
  def insert(item: T): Unit = {
    currentBatch.+=(item)

    if (currentBatch.length >= threshold) {
      flush
    }
  }

  /** execute the batch insert */
  def flush: Unit = {
    import database.api._
    blockingDatabase.runUnit(inserter.++=(currentBatch))
    currentBatch.clear
  }
}
