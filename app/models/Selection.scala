package models

import java.util.{Date,UUID}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import models.pagination.{Page,PageInfo,PageRequest}

trait SelectionLike {
  val id: UUID
  val timestamp: Date
  val request: SelectionRequest
  def getDocumentIds(page: PageRequest): Future[Page[Long]]
  def getAllDocumentIds: Future[Seq[Long]]
  def getDocumentCount: Future[Int]
}

case class Selection(
  override val id: UUID,
  override val timestamp: Date,
  override val request: SelectionRequest,
  val documentIds: Seq[Long]
) extends SelectionLike {
  override def getDocumentIds(page: PageRequest) = {
    Future.successful(Page(
      documentIds.drop(page.offset).take(page.limit),
      PageInfo(page, documentIds.length)
    ))
  }
  override def getAllDocumentIds = Future.successful(documentIds)
  override def getDocumentCount = Future.successful(documentIds.size)
}

object Selection {
  def apply(request: SelectionRequest, documentIds: Seq[Long]): Selection = Selection(
    UUID.randomUUID(),
    new Date(),
    request,
    documentIds
  )
}
