package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.postgres.PostgresqlEnum

import org.overviewproject.tree.DocumentSetCreationJobType
import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value

  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
  val Preparing = Value(4, "PREPARING")
}

import DocumentSetCreationJobState._

case class DocumentSetCreationJob(
  id: Long = 0L,
  documentSetId: Long = 0L,
  @Column("type") jobType: DocumentSetCreationJobType.Value,
  lang: String = "en",
  suppliedStopWords: String = "",
  importantWords: String = "",
  documentcloudUsername: Option[String] = None,
  documentcloudPassword: Option[String] = None,
  splitDocuments: Boolean = false,
  contentsOid: Option[Long] = None,
  sourceDocumentSetId: Option[Long] = None,
  fileGroupId: Option[Long] = None,
  state: DocumentSetCreationJobState.Value,
  fractionComplete: Double = 0.0,
  statusDescription: String = "") extends KeyedEntity[Long] {

  def this() = this(jobType = DocumentSetCreationJobType.DocumentCloud, state = NotStarted)

  override def isPersisted(): Boolean = (id > 0)
}
