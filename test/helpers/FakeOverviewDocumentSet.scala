package helpers

import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob }

case class FakeOverviewDocumentSet(
    override val id: Long = 1l, 
    override val title: String = "a title",
    override val query: String = "a query", 
    override val documentProcessingErrorCount: Int = 0) extends OverviewDocumentSet {

  override val owner = null
  override val createdAt = null
  override val documentCount = 15
  override val isPublic = false

  override def cloneForUser(cloneOwnerId: Long): OverviewDocumentSet = this
  override def addViewer(email: String): Unit = {}
  override def removeViewer(email: String): Unit = {}
}
