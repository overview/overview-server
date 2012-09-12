package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne
import org.squeryl.PrimitiveTypeMode._

case class DocumentSetCreationJob(
    @Column("document_set_id") val documentSetId: Long = 0,
    @Column("documentcloud_username") val username: Option[String] = None,
    @Column("documentcloud_password") val password: Option[String] = None,
    val state: DocumentSetCreationJob.State = DocumentSetCreationJob.State.NotStarted,
    @Column("fraction_complete") val fractionComplete: Double = 0.0,
    @Column("status_description") val stateDescription: String = ""
   ) extends KeyedEntity[Long] {
  override val id: Long = 0

  def this() = this(state=DocumentSetCreationJob.State.NotStarted) // rrgh, Squeryl Enumerations

  lazy val documentSet: ManyToOne[DocumentSet] = Schema.documentSetDocumentSetCreationJobs.right(this);

  def position: Long = {
    val queue = from(Schema.documentSetCreationJobs)(ds =>
      where(ds.state === DocumentSetCreationJob.State.NotStarted) select(ds.id) orderBy(ds.id))

    queue.toSeq.indexOf(id)
  }
}

object DocumentSetCreationJob {
  object State extends Enumeration {
    val NotStarted = Value(0, "NOT_STARTED")
    val InProgress = Value(1, "IN_PROGRESS")
    val Error = Value(2, "ERROR")
  }
  type State = State.Value
}
