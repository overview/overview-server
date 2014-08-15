package org.overviewproject.jobhandler.filegroup.task

trait DocumentIdGenerator {

  val documentSetId: Long

  def nextId: Long = {
    lastUsedId += 1
    (documentSetId << 32) | lastUsedId
  }
  
  protected def largestExistingId: Long
  
  private var lastUsedId = largestExistingId
}