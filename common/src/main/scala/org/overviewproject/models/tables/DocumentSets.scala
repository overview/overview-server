package org.overviewproject.models.tables

import java.sql.Timestamp

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSet

class DocumentSetsImpl(tag: Tag) extends Table[DocumentSet](tag, "document_set") {
  def id = column[Long]("id", O.PrimaryKey)
  def title = column[String]("title")
  def query = column[Option[String]]("query")
  def isPublic = column[Boolean]("public")
  def createdAt = column[Timestamp]("created_at")
  def documentCount = column[Int]("document_count")
  def documentProcessingErrorCount = column[Int]("document_processing_error_count")
  def importOverflowCount = column[Int]("import_overflow_count")
  def uploadedFileId = column[Option[Long]]("uploaded_file_id")
  def deleted = column[Boolean]("deleted")

  def * = (
    id,
    title,
    query,
    isPublic,
    createdAt,
    documentCount,
    documentProcessingErrorCount,
    importOverflowCount,
    uploadedFileId,
    deleted
  ) <> ((DocumentSet.apply _).tupled, DocumentSet.unapply)

  def createAttributes = (
    title,
    query,
    isPublic,
    createdAt,
    documentCount,
    documentProcessingErrorCount,
    importOverflowCount,
    uploadedFileId,
    deleted
  ) <> (
    DocumentSet.CreateAttributes.tupled,
    DocumentSet.CreateAttributes.unapply
  )
}

object DocumentSets extends TableQuery(new DocumentSetsImpl(_))
