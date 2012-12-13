package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne
import org.squeryl.PrimitiveTypeMode._


object DocumentSetCreationJobState extends Enumeration {
  type DocumentSetCreationJobState = Value

  val NotStarted = Value(0, "NOT_STARTED")
  val InProgress = Value(1, "IN_PROGRESS")
  val Error = Value(2, "ERROR")
}

import DocumentSetCreationJobState._

case class DocumentSetCreationJob(
  val documentSetId: Long = 0,
  val documentcloudUsername: Option[String] = None,
  val documentcloudPassword: Option[String] = None,
  val state: DocumentSetCreationJobState = NotStarted,
  val fractionComplete: Double = 0.0,
  val statusDescription: String = "") extends KeyedEntity[Long] {
  override val id: Long = 0

  def this() = this(state = NotStarted)

  lazy val documentSet: DocumentSet = Schema.documentSetDocumentSetCreationJobs.right(this).single

  lazy val user: User = documentSet.users.single

  def jobsAheadInQueue: Long = {
    val queue = from(Schema.documentSetCreationJobs)(ds =>
      where(ds.state === NotStarted) select (ds.id) orderBy (ds.id))

    queue.toSeq.indexOf(id) + 1
  }
}

object DocumentSetCreationJob {
  def all() = from(Schema.documentSetCreationJobs)(j => select(j).orderBy(j.id.asc))
}
