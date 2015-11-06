package com.overviewdocs.jobhandler.filegroup

import scala.collection.mutable

/** Generates reusable IDs for concurrent workers.
  *
  * Usage:
  *
  *   pool = TaskIdPool()
  *   pool.acquireId() // returns 1
  *   pool.acquireId() // returns 2
  *   pool.releaseId(1)
  *   pool.acquireId() // returns 1
  *
  * This class is thread-safe. That's the whole point.
  */
class TaskIdPool {
  private var usedIds: mutable.Set[Int] = mutable.Set()

  def acquireId: Int = synchronized {
    var ret = 1
    while (usedIds.contains(ret)) {
      ret += 1
    }
    usedIds.+=(ret)
    ret
  }

  def releaseId(id: Int): Unit = synchronized {
    usedIds.-=(id)
  }
}

object TaskIdPool {
  def apply(): TaskIdPool = new TaskIdPool
}
