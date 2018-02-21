package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentProcessingError

class DocumentProcessingErrorsImpl(tag: Tag) extends Table[DocumentProcessingError](tag, "document_processing_error") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def file2Id = column[Option[Long]]("file2_id")
  def textUrl = column[String]("text_url")
  def message = column[String]("message")
  def statusCode = column[Option[Int]]("status_code")
  def headers = column[Option[String]]("headers")

  def * = (id, documentSetId, file2Id, textUrl, message, statusCode, headers) <>
    ((DocumentProcessingError.apply _).tupled, DocumentProcessingError.unapply)

  def createAttributes = (
    documentSetId,
    file2Id,
    textUrl,
    message,
    statusCode,
    headers
  ) <> (
    DocumentProcessingError.CreateAttributes.tupled,
    DocumentProcessingError.CreateAttributes.unapply
  )
}

object DocumentProcessingErrors extends TableQuery(new DocumentProcessingErrorsImpl(_))
