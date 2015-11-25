package com.overviewdocs.models.tables

import java.sql.Timestamp

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.DocumentSet

class DocumentSetsImpl(tag: Tag) extends Table[DocumentSet](tag, "document_set") {
  def id = column[Long]("id", O.PrimaryKey)
  def title = column[String]("title")
  def query = column[Option[String]]("query")
  def isPublic = column[Boolean]("public")
  def createdAt = column[Timestamp]("created_at")
  def documentCount = column[Int]("document_count")
  def documentProcessingErrorCount = column[Int]("document_processing_error_count")
  def importOverflowCount = column[Int]("import_overflow_count")
  def metadataSchema = column[MetadataSchema]("metadata_schema")
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
    metadataSchema,
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
    metadataSchema,
    deleted
  ) <> (
    DocumentSet.CreateAttributes.tupled,
    DocumentSet.CreateAttributes.unapply
  )
}

object DocumentSets extends TableQuery(new DocumentSetsImpl(_))
