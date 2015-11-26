package com.overviewdocs.models.tables

import java.time.Instant

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.CloneJob

class CloneJobsImpl(tag: Tag) extends Table[CloneJob](tag, "clone_job") {
  def id = column[Int]("id", O.PrimaryKey)
  def sourceDocumentSetId = column[Long]("source_document_set_id")
  def destinationDocumentSetId = column[Long]("destination_document_set_id")
  def stepNumber = column[Short]("step_number")
  def cancelled = column[Boolean]("cancelled")
  def createdAt = column[Instant]("created_at")

  def * = (id, sourceDocumentSetId, destinationDocumentSetId, stepNumber, cancelled, createdAt)
    .<>((CloneJob.apply _).tupled, CloneJob.unapply)

  def createAttributes = (sourceDocumentSetId, destinationDocumentSetId, stepNumber, cancelled, createdAt)
    .<>((CloneJob.CreateAttributes.apply _).tupled, CloneJob.CreateAttributes.unapply)
}

object CloneJobs extends TableQuery(new CloneJobsImpl(_))
