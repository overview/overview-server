package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.postgres.PostgresqlEnum

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne



object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value

  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
}

import DocumentSetCreationJobState._

// Use new approach to enums for type
class DocumentSetCreationJobType(v: String) extends PostgresqlEnum(v, "document_set_creation_job_type")

object DocumentSetCreationJobType {
  val DocumentCloudJob = new DocumentSetCreationJobType("DocumentCloudJob")
  val CsvImportJob = new DocumentSetCreationJobType("CsvImportJob")
  val CloneJob = new DocumentSetCreationJobType("CloneJob")
}

import DocumentSetCreationJobType._

case class DocumentSetCreationJob(
  documentSetId: Long = 0,
  @Column("type") documentSetCreationJobType: DocumentSetCreationJobType,
  documentcloudUsername: Option[String] = None,
  documentcloudPassword: Option[String] = None,
  contentsOid: Option[Long] = None,
  state: DocumentSetCreationJobState = NotStarted,
  fractionComplete: Double = 0.0,
  statusDescription: String = "",
  id: Long = 0) extends KeyedEntity[Long] {

  def this() = this(documentSetCreationJobType = DocumentCloudJob, state = NotStarted)

  override def isPersisted(): Boolean = (id > 0)

}

