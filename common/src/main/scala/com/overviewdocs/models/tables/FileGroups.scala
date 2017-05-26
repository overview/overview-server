package com.overviewdocs.models.tables

import play.api.libs.json.JsObject
import java.time.Instant

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.FileGroup

class FileGroupsImpl(tag: Tag) extends Table[FileGroup](tag, "file_group") {
  def id = column[Long]("id", O.PrimaryKey)
  def userEmail = column[String]("user_email")
  def apiToken = column[Option[String]]("api_token")
  def deleted = column[Boolean]("deleted")
  def addToDocumentSetId = column[Option[Long]]("add_to_document_set_id")
  def lang = column[Option[String]]("lang")
  def splitDocuments = column[Option[Boolean]]("split_documents")
  def ocr = column[Option[Boolean]]("ocr")
  def nFiles = column[Option[Int]]("n_files")
  def nBytes = column[Option[Long]]("n_bytes")
  def nFilesProcessed = column[Option[Int]]("n_files_processed")
  def nBytesProcessed = column[Option[Long]]("n_bytes_processed")
  def estimatedCompletionTime = column[Option[Instant]]("estimated_completion_time")
  def metadataJson = column[JsObject]("metadata_json_text") // assumes a certaian DocumentSet.metadataSchema
  
  def * = (
    id,
    userEmail,
    apiToken,
    deleted,
    addToDocumentSetId,
    lang,
    splitDocuments,
    ocr,
    nFiles,
    nBytes,
    nFilesProcessed,
    nBytesProcessed,
    estimatedCompletionTime,
    metadataJson
  ) <> ((FileGroup.apply _).tupled, FileGroup.unapply)
}

object FileGroups extends TableQuery(new FileGroupsImpl(_))
