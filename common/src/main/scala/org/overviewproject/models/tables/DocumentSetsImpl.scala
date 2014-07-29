package org.overviewproject.models.tables

import java.sql.Timestamp

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.DocumentSet // should be models.DocumentSet

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
  def version = column[Int]("version")
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
    version,
    deleted
  ) <> ((DocumentSet.apply _).tupled, DocumentSet.unapply)
}

object documentSets extends TableQuery(new DocumentSetsImpl(_))
