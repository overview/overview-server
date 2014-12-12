package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentProcessingError

class DocumentProcessingErrorsImpl(tag: Tag) extends Table[DocumentProcessingError](tag, "document_processing_error") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def textUrl = column[String]("text_url")
  def message = column[String]("message")
  def statusCode = column[Option[Int]]("status_code")
  def headers = column[Option[String]]("headers")

  def * = (id, documentSetId, textUrl, message, statusCode, headers) <>
    ((DocumentProcessingError.apply _).tupled, DocumentProcessingError.unapply)
}

object DocumentProcessingErrors extends TableQuery(new DocumentProcessingErrorsImpl(_))