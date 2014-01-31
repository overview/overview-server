package org.overviewproject.persistence

class NodeIdGenerator(documentSetId: Long, treeId: Long) {
  private var nodeIndex = 0
  private val treeIndex = treeId & ~(documentSetId << 32)
  
  def next: Long = {
    nodeIndex += 1
    (documentSetId << 32) | (treeIndex << 20) | nodeIndex
  }
}
