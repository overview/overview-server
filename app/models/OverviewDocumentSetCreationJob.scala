package models

import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.{DocumentSetCreationJob,UploadedFile}
import models.orm.DocumentSet
import models.orm.finders.DocumentSetCreationJobFinder

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
  import org.overviewproject.postgres.SquerylEntrypoint._
  import models.orm.Schema.{ documentSetCreationJobs, documentSetDocumentSetCreationJobs }

  def all: Seq[OverviewDocumentSetCreationJob] = { // FIXME paginate (well, remove, really. But otherwise, paginate.)
    DocumentSetCreationJobFinder.all.map(new OverviewDocumentSetCreationJobImpl(_)).toSeq
  }

  def findByDocumentSetId(documentSetId: Long): Option[OverviewDocumentSetCreationJob] = {
    val documentSetCreationJob = from(documentSetCreationJobs)(j => where(j.documentSetId === documentSetId) select (j)).headOption

    documentSetCreationJob.map(new OverviewDocumentSetCreationJobImpl(_))
  }

  def findByUserWithDocumentSet(userEmail: String, pageSize: Int, page: Int)
    : ResultPage[(OverviewDocumentSetCreationJob, OverviewDocumentSet)] = {

    val tuples = DocumentSetCreationJobFinder.byUserWithDocumentSetsAndUploadedFiles(userEmail)
    ResultPage(tuples, pageSize, page).map { tuple: (DocumentSetCreationJob, DocumentSet, Option[UploadedFile]) =>
      (OverviewDocumentSetCreationJob(tuple._1), OverviewDocumentSet(tuple._2, tuple._3))
    }
  }
  
  def cancelJobsWithSourceDocumentSetId(sourceDocumentSetId: Long): Seq[OverviewDocumentSetCreationJob] = {
    val cloneJobs = documentSetCreationJobs.where(dscj =>
      dscj.sourceDocumentSetId === Some(sourceDocumentSetId)  
    ).forUpdate

    cloneJobs.map(j => new OverviewDocumentSetCreationJobImpl(j.copy(state = Cancelled)).save).toSeq
  }

  def cancelJobWithDocumentSetId(documentSetId: Long): Option[OverviewDocumentSetCreationJob] = {
    val cancellableJob = documentSetCreationJobs.where(dscj =>
      dscj.documentSetId === documentSetId and
        (dscj.state === InProgress or dscj.state === Cancelled)).forUpdate.headOption
        
    cancellableJob.map { job =>
      new OverviewDocumentSetCreationJobImpl(job.copy(state = Cancelled)).save
    }
  }

  def apply(ormJob: DocumentSetCreationJob) : OverviewDocumentSetCreationJob = {
    if (ormJob.documentSetCreationJobType == DocumentCloudJob
      && ormJob.documentcloudUsername.isDefined
      && ormJob.documentcloudPassword.isDefined) {

      new JobWithDocumentCloudCredentials(ormJob)
    } else {
      new OverviewDocumentSetCreationJobImpl(ormJob)
    }
  }

  private class OverviewDocumentSetCreationJobImpl(val documentSetCreationJob: DocumentSetCreationJob) extends OverviewDocumentSetCreationJob {
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
      new OverviewDocumentSetCreationJobImpl(documentSetCreationJob.copy(state = newState))
    }

    def withDocumentCloudCredentials(username: String, password: String): OverviewDocumentSetCreationJob with DocumentCloudCredentials = {
      val credentialedJob = documentSetCreationJob.copy(documentcloudUsername = Some(username), documentcloudPassword = Some(password))
      new JobWithDocumentCloudCredentials(credentialedJob)
    }

    def save: OverviewDocumentSetCreationJob = {
      val ret = documentSetCreationJobs.insertOrUpdate(documentSetCreationJob)

      new OverviewDocumentSetCreationJobImpl(ret)
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
