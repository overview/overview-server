package models

import java.util.UUID
import scala.concurrent.Future

import models.pagination.{Page,PageInfo,PageRequest}

/** A list of document IDs, calculated once and analyzed one or more times. */
trait Selection {
  /** Unique ID, used for caching and retrieving */
  val id: UUID

  /** Messages explaining why the search results may be inaccurate.
    *
    * Yup, inaccurate. We believe that real users would prefer inaccurate
    * results to an error message when, say, they search for "a*" and we refuse
    * to query Lucene for 20,000 search terms over 10,000,000 documents.
    */
  val warnings: List[SelectionWarning]

  def getDocumentIds(page: PageRequest): Future[Page[Long]]
  def getAllDocumentIds: Future[Array[Long]]
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
  val documentIds: Array[Long],
  override val warnings: List[SelectionWarning]
) extends Selection {
  override def getDocumentIds(page: PageRequest) = {
    val iterator = if (page.reverse) {
      documentIds.reverseIterator
    } else {
      documentIds.iterator
    }

    Future.successful(Page(
      iterator.drop(page.offset).take(page.limit).toArray,
      PageInfo(page, documentIds.length)
    ))
  }
  override def getAllDocumentIds = Future.successful(documentIds)
  override def getDocumentCount = Future.successful(documentIds.size)
}

object InMemorySelection {
  def apply(documentIds: Array[Long]): InMemorySelection = {
    InMemorySelection(documentIds, Nil)
  }

  def apply(documentIds: Array[Long], warnings: List[SelectionWarning]): InMemorySelection = {
    InMemorySelection(UUID.randomUUID(), documentIds, warnings)
  }
}
