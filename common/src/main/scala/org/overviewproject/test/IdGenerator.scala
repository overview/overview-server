package org.overviewproject.test

object IdGenerator {
  private var nodeIndex = 0
  private var documentIndex = 0
  
  private def makeId(documentSetId: Long, index: Int): Long = (documentSetId << 32) | index
  
  def nextNodeId(documentSetId: Long): Long = {
    nodeIndex += 1
    makeId(documentSetId, nodeIndex)
  }
  
  def nextDocumentId(documentSetId: Long): Long = {
    documentIndex += 1
    makeId(documentSetId, documentIndex)
  }
}