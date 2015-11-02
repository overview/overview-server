package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentSetCreationJob
import com.overviewdocs.models.DocumentSetCreationJobType
import com.overviewdocs.models.DocumentSetCreationJobState

class DocumentSetCreationJobsImpl(tag: Tag) extends Table[DocumentSetCreationJob](tag, "document_set_creation_job") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def jobType = column[DocumentSetCreationJobType.Value]("type")(jobTypeColumnType)
  def retryAttempts = column[Int]("retry_attempts")
  def lang = column[String]("lang")
  def suppliedStopWords = column[String]("supplied_stop_words")
  def importantWords = column[String]("important_words")
  def splitDocuments = column[Boolean]("split_documents")
  def documentcloudUsername = column[Option[String]]("documentcloud_username")
  def documentcloudPassword = column[Option[String]]("documentcloud_password")
  def contentsOid = column[Option[Long]]("contents_oid")
  def sourceDocumentSetId = column[Option[Long]]("source_document_set_id")
  def treeTitle = column[Option[String]]("tree_title")
  def treeDescription = column[Option[String]]("tree_description")
  def tagId = column[Option[Long]]("tag_id")
  def state = column[DocumentSetCreationJobState.Value]("state")(stateColumnType)
  def fractionComplete = column[Double]("fraction_complete")
  def statusDescription = column[String]("status_description")
  def canBeCancelled = column[Boolean]("can_be_cancelled")

  def * =
    (id, documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords, splitDocuments,
      documentcloudUsername, documentcloudPassword, contentsOid, sourceDocumentSetId,
      treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled) <>
      ((DocumentSetCreationJob.apply _).tupled, DocumentSetCreationJob.unapply)

  def createAttributes =
    (documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords, splitDocuments,
      documentcloudUsername, documentcloudPassword, contentsOid, sourceDocumentSetId,
      treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled) <>
      (DocumentSetCreationJob.CreateAttributes.tupled, DocumentSetCreationJob.CreateAttributes.unapply)
}

object DocumentSetCreationJobs extends TableQuery(new DocumentSetCreationJobsImpl(_))
