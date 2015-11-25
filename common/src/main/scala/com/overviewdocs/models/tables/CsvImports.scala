package com.overviewdocs.models.tables

import java.nio.charset.Charset
import java.time.Instant

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.CsvImport

class CsvImportsImpl(tag: Tag) extends Table[CsvImport](tag, "csv_import") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def filename = column[String]("filename")
  def charset = column[Charset]("charset")
  def lang = column[String]("lang")
  def loid = column[Long]("loid")
  def nBytes = column[Long]("n_bytes")
  def nBytesProcessed = column[Long]("n_bytes_processed")
  def nDocuments = column[Int]("n_documents")
  def cancelled = column[Boolean]("cancelled")
  def estimatedCompletionTime = column[Option[Instant]]("estimated_completion_time")
  def createdAt = column[Instant]("created_at")

  def * = (
    id, documentSetId, filename, charset, lang, loid, nBytes, nBytesProcessed,
    nDocuments, cancelled, estimatedCompletionTime, createdAt
  ).<>((CsvImport.apply _).tupled, CsvImport.unapply)

  def createAttributes = (
    documentSetId, filename, charset, lang, loid, nBytes, nBytesProcessed,
    nDocuments, cancelled, estimatedCompletionTime, createdAt
  ).<>((CsvImport.CreateAttributes.apply _).tupled, CsvImport.CreateAttributes.unapply)
}

object CsvImports extends TableQuery(new CsvImportsImpl(_))
