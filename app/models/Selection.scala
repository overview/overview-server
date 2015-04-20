package models

import java.util.UUID
import scala.concurrent.Future

import models.pagination.{Page,PageInfo,PageRequest}

/** A list of document IDs, calculated once and analyzed one or more times. */
trait Selection {
  val id: UUID
  def getDocumentIds(page: PageRequest): Future[Page[Long]]
  def getAllDocumentIds: Future[Seq[Long]]
  def getDocumentCount: Future[Int]
}

/** A list of document IDs in memory.
  *
  * This is useful in testing and processing. It can take a long time (even
  * seconds) to generate an InMemorySelection and it doesn't persist between
  * HTTP requests. Prefer RedisSelection when possible.
  */
case class InMemorySelection(
  override val id: UUID,
  val documentIds: Seq[Long]
) extends Selection {
  override def getDocumentIds(page: PageRequest) = {
    Future.successful(Page(
      documentIds.drop(page.offset).take(page.limit),
      PageInfo(page, documentIds.length)
    ))
  }
  override def getAllDocumentIds = Future.successful(documentIds)
  override def getDocumentCount = Future.successful(documentIds.size)
}

object InMemorySelection {
  def apply(documentIds: Seq[Long]): Selection = InMemorySelection(UUID.randomUUID(), documentIds)
}
