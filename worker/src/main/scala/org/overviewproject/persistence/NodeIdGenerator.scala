package org.overviewproject.persistence

class NodeIdGenerator(treeId: Long) {
  private val documentSetId = treeId >> 32
  private val treeIndex = treeId & ~(documentSetId << 32)
  private var nodeIndex = 0
  
  def next: Long = {
    nodeIndex += 1
    (documentSetId << 32) | (treeIndex << 20) | nodeIndex
  }
}
