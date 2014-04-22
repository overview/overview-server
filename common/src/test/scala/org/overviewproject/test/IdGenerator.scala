package org.overviewproject.test

/**
 * A simple way to provide ids for tables in the database
 * that no longer depend on a SEQUENCE.
 * Intended for tests only. In production, separate indeces for
 * each document set are needed.
 */
object IdGenerator {
  private var nodeIndex = 0
  private var documentIndex = 0
  private var treeIndex = 0
  
  private def makeId(documentSetId: Long, index: Int): Long = (documentSetId << 32) | index
  
  def nextNodeId(documentSetId: Long): Long = {
    nodeIndex += 1
    makeId(documentSetId, nodeIndex)
  }
  
  def nextDocumentId(documentSetId: Long): Long = {
    documentIndex += 1
    makeId(documentSetId, documentIndex)
  }
  
  def nextTreeId(documentSetId: Long): Long = {
    treeIndex += 1
    makeId(documentSetId, treeIndex)
  }
}