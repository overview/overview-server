package models.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.dsl.ManyToOne

case class DocumentSetCreationJob(
    @Column("document_set_id")
    val documentSetId: Long = 0,
    val state: DocumentSetCreationJob.State = DocumentSetCreationJob.State.NOT_STARTED,
    val fraction_complete: Double = 0.0,
    @Column("status_description")
    val stateDescription: String = ""
   ) extends KeyedEntity[Long] {
  override val id: Long = 0

  def this() = this(state=DocumentSetCreationJob.State.NOT_STARTED) // rrgh, Squeryl Enumerations

  lazy val documentSet: ManyToOne[DocumentSet] = Schema.documentSetDocumentSetCreationJobs.right(this);
}

object DocumentSetCreationJob {
  object State extends Enumeration {
    val NOT_STARTED = Value(0, "NOT_STARTED")
    val IN_PROGRESS = Value(1, "IN_PROGRESS")
    val ERROR = Value(2, "ERROR")
  }
  type State = State.Value
}
