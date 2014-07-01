package org.overviewproject.persistence

class NodeIdGenerator(treeId: Long) {
  private val documentSetId = treeId >> 32
  private val treeIndex = treeId & ~(documentSetId << 32)
  private val base = (documentSetId << 32) | (treeIndex << 20) | 0
  private var n = 0

  val rootId: Long = base | 1

  def next : Long = {
    n += 1
    base | n
  }
}
