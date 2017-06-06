package com.overviewdocs.models.tables

import java.time.Instant

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentSetReindexJob

class DocumentSetReindexJobsImpl(tag: Tag)
extends Table[DocumentSetReindexJob](tag, "document_set_reindex_job") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def lastRequestedAt = column[Instant]("last_requested_at")
  def startedAt = column[Option[Instant]]("started_at")
  def progress = column[Double]("progress")

  def * = (
    id, documentSetId, lastRequestedAt, startedAt, progress
  ).<>((DocumentSetReindexJob.apply _).tupled, DocumentSetReindexJob.unapply)
}

object DocumentSetReindexJobs extends TableQuery(new DocumentSetReindexJobsImpl(_))
