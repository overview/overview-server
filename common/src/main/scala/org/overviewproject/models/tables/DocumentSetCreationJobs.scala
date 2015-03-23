package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobType
import org.overviewproject.models.DocumentSetCreationJobState

trait DocumentSetCreationJobMappings {
  implicit val jobTypeColumnType =
    MappedColumnType.base[DocumentSetCreationJobType.Value, Int](_.id, DocumentSetCreationJobType.apply)

  implicit val stateColumnType =
    MappedColumnType.base[DocumentSetCreationJobState.Value, Int](_.id, DocumentSetCreationJobState.apply)
}

class DocumentSetCreationJobsImpl(tag: Tag) extends Table[DocumentSetCreationJob](tag, "document_set_creation_job")
    with DocumentSetCreationJobMappings {

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def jobType = column[DocumentSetCreationJobType.Value]("type")
  def retryAttempts = column[Int]("retry_attempts")
  def lang = column[String]("lang")
  def suppliedStopWords = column[String]("supplied_stop_words")
  def importantWords = column[String]("important_words")
  def splitDocuments = column[Boolean]("split_documents")
  def documentcloudUsername = column[Option[String]]("documentcloud_username")
  def documentcloudPassword = column[Option[String]]("documentcloud_password")
  def contentsOid = column[Option[Long]]("contents_oid")
  def fileGroupId = column[Option[Long]]("file_group_id")
  def sourceDocumentSetId = column[Option[Long]]("source_document_set_id")
  def treeTitle = column[Option[String]]("tree_title")
  def treeDescription = column[Option[String]]("tree_description")
  def tagId = column[Option[Long]]("tag_id")
  def state = column[DocumentSetCreationJobState.Value]("state")
  def fractionComplete = column[Double]("fraction_complete")
  def statusDescription = column[String]("status_description")
  def canBeCancelled = column[Boolean]("can_be_cancelled")

  def * =
    (id, documentSetId, jobType, retryAttempts, lang, suppliedStopWords, importantWords, splitDocuments,
      documentcloudUsername, documentcloudPassword, contentsOid, fileGroupId, sourceDocumentSetId,
      treeTitle, treeDescription, tagId, state, fractionComplete, statusDescription, canBeCancelled) <>
      ((DocumentSetCreationJob.apply _).tupled, DocumentSetCreationJob.unapply)

}

object DocumentSetCreationJobs extends TableQuery(new DocumentSetCreationJobsImpl(_))
