package helpers

import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob }

case class FakeOverviewDocumentSet(
    id: Long = 1l, 
    title: String = "a title",
    query: String = "a query", 
    creationJob: Option[OverviewDocumentSetCreationJob] = None, errorCount: Int = 0) extends OverviewDocumentSet {

  val owner = null
  val createdAt = null
  val documentCount = 15
  val isPublic = false

  def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet = this
}
