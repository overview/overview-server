package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne
import org.squeryl.PrimitiveTypeMode._

object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value

  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
  val Cancelled = Value(3, "CANCELLED")
}

import DocumentSetCreationJobState._

case class DocumentSetCreationJob(
  documentSetId: Long = 0,
  documentcloudUsername: Option[String] = None,
  documentcloudPassword: Option[String] = None,
  state: DocumentSetCreationJobState = NotStarted,
  fractionComplete: Double = 0.0,
  statusDescription: String = "",
  id: Long = 0) extends KeyedEntity[Long] {

  def this() = this(state = NotStarted)

  override def isPersisted(): Boolean = (id > 0)

}

