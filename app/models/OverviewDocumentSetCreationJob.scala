package models

import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.DocumentSetCreationJob

trait OverviewDocumentSetCreationJob {
  val id: Long
  val documentSetId: Long
  val state: DocumentSetCreationJobState
  val fractionComplete: Double
  val stateDescription: String

  def jobsAheadInQueue: Int

  def withState(newState: DocumentSetCreationJobState): OverviewDocumentSetCreationJob
  def withDocumentCloudCredentials(username: String, password: String): OverviewDocumentSetCreationJob with DocumentCloudCredentials

  def save: OverviewDocumentSetCreationJob
}

trait DocumentCloudCredentials {
  val username: String
  val password: String
}

object OverviewDocumentSetCreationJob {
  import org.squeryl.PrimitiveTypeMode._
  import models.orm.Schema.{ documentSetCreationJobs, documentSetDocumentSetCreationJobs }

  def apply(documentSet: OverviewDocumentSet): OverviewDocumentSetCreationJob = {
    val documentSetCreationJob = DocumentSetCreationJob(documentSet.id, state = NotStarted)
    OverviewDocumentSetCreationJobImpl(documentSetCreationJob)
  }

  def all: Seq[OverviewDocumentSetCreationJob] = {
    from(documentSetCreationJobs)(j => select(j).orderBy(j.id.asc)).toSeq.map(OverviewDocumentSetCreationJobImpl)
  }

  def findByDocumentSetId(documentSetId: Long): Option[OverviewDocumentSetCreationJob] = {
    val documentSetCreationJob = from(documentSetCreationJobs)(j => where(j.documentSetId === documentSetId) select (j)).headOption

    documentSetCreationJob.map(OverviewDocumentSetCreationJobImpl)
  }

  def cancelJobWithDocumentSetId(documentSetId: Long): Option[OverviewDocumentSetCreationJob] = {
    val cancellableJob = documentSetCreationJobs.where(dscj =>
      dscj.documentSetId === documentSetId and
        (dscj.state === InProgress or dscj.state === Cancelled)).forUpdate.headOption
        
    cancellableJob.map { job =>
      new OverviewDocumentSetCreationJobImpl(job.copy(state = Cancelled)) save
    }
  }

  private case class OverviewDocumentSetCreationJobImpl(documentSetCreationJob: DocumentSetCreationJob) extends OverviewDocumentSetCreationJob {
    val id: Long = documentSetCreationJob.id
    val documentSetId: Long = documentSetCreationJob.documentSetId
    val state: DocumentSetCreationJobState = documentSetCreationJob.state
    val fractionComplete: Double = documentSetCreationJob.fractionComplete
    val stateDescription: String = documentSetCreationJob.statusDescription

    def jobsAheadInQueue: Int = {
      val queue = from(documentSetCreationJobs)(ds =>
        where(ds.state === NotStarted) select (ds.id) orderBy (ds.id))

      queue.toSeq.indexOf(id) + 1
    }

    def withState(newState: DocumentSetCreationJobState): OverviewDocumentSetCreationJob = {
      OverviewDocumentSetCreationJobImpl(documentSetCreationJob.copy(state = newState))
    }

    def withDocumentCloudCredentials(username: String, password: String): OverviewDocumentSetCreationJob with DocumentCloudCredentials = {
      val credentialedJob = documentSetCreationJob.copy(documentcloudUsername = Some(username), documentcloudPassword = Some(password))
      new JobWithDocumentCloudCredentials(credentialedJob)
    }

    def save: OverviewDocumentSetCreationJob = {
      documentSetCreationJobs.insertOrUpdate(documentSetCreationJob)

      copy(documentSetCreationJob)
    }
  }

  private class JobWithDocumentCloudCredentials(documentSetCreationJob: DocumentSetCreationJob)
    extends OverviewDocumentSetCreationJobImpl(documentSetCreationJob) with DocumentCloudCredentials {
    require(documentSetCreationJob.documentcloudUsername.isDefined)
    require(documentSetCreationJob.documentcloudPassword.isDefined)

    val username = documentSetCreationJob.documentcloudUsername.get
    val password = documentSetCreationJob.documentcloudPassword.get

  }

}