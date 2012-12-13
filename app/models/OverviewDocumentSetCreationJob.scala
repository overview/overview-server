package models

import models.orm.DocumentSetCreationJobState._
import models.orm.DocumentSetCreationJob

trait OverviewDocumentSetCreationJob {
  val id: Long
  val documentSetId: Long
  val state: DocumentSetCreationJobState

  def save: OverviewDocumentSetCreationJob
}

object OverviewDocumentSetCreationJob {
  import org.squeryl.PrimitiveTypeMode._
  import models.orm.Schema.documentSetCreationJobs

  def apply(documentSet: OverviewDocumentSet): OverviewDocumentSetCreationJob = {
    val documentSetCreationJob = DocumentSetCreationJob(documentSet.id, state = NotStarted)
    new OverviewDocumentSetCreationJobImpl(documentSetCreationJob)
  }

  def all: Seq[OverviewDocumentSetCreationJob] = {
    from(documentSetCreationJobs)(j => select(j).orderBy(j.id.asc)).toSeq.map(OverviewDocumentSetCreationJobImpl)
  }

  private case class OverviewDocumentSetCreationJobImpl(documentSetCreationJob: DocumentSetCreationJob) extends OverviewDocumentSetCreationJob {
    val id: Long = documentSetCreationJob.id
    val documentSetId: Long = documentSetCreationJob.documentSetId
    val state: DocumentSetCreationJobState = documentSetCreationJob.state

    def save: OverviewDocumentSetCreationJob = {
      documentSetCreationJobs.insertOrUpdate(documentSetCreationJob)

      copy(documentSetCreationJob)
    }
  }
}