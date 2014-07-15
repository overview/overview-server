package controllers.admin

import play.api.mvc.Controller

import org.overviewproject.jobs.models.{ CancelFileUpload, Delete }
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.util.DocumentSetDeletionComponents
import controllers.util.JobContextChecker
import models.orm.User
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.finders.DocumentSetFinder

trait ImportJobController extends Controller with JobContextChecker {

  trait Storage {
    def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)]
    def findDocumentSetByJob(jobId: Long): Option[DocumentSet]
    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob]
    def deleteDocumentSet(documentSet: DocumentSet): Unit
  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  val storage: Storage
  val jobQueue: JobMessageQueue

  def index() = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    val jobs = storage.findAllDocumentSetCreationJobs

    Ok(views.html.admin.ImportJob.index(request.user, jobs))
  }

  def delete(importJobId: Long) = AuthorizedAction.inTransaction(adminUser) { implicit request =>

    val documentSet = storage.findDocumentSetByJob(importJobId)
    def onDocumentSet[A](f: DocumentSet => A): Option[A] =
      documentSet.map(f)

    implicit val cancelledJob = onDocumentSet(ds => storage.cancelJob(ds.id)).flatten
    def id = documentSet.get.id

    if (runningInWorker) {
      onDocumentSet(storage.deleteDocumentSet)
      jobQueue.send(Delete(id, waitForJobRemoval = true)) // wait for worker to stop clustering and remove job
      flashSuccess
    } else if (notRunning) {
      onDocumentSet(storage.deleteDocumentSet)
      jobQueue.send(Delete(id, waitForJobRemoval = false)) // don't wait for worker
      flashSuccess
    } else if (runningInTextExtractionWorker && validTextExtractionJob) {
      jobQueue.send(CancelFileUpload(id, cancelledJob.get.fileGroupId.get))
      flashSuccess
    } else flashFailure

  }

  private def flashSuccess = Redirect(routes.ImportJobController.index())
    .flashing("success" -> "The document set and the job that spawned it have been deleted.")

  private def flashFailure = Redirect(routes.ImportJobController.index())
    .flashing("warning" -> "Could not delete job: it does not exist. Has it completed?")

}

object ImportJobController extends ImportJobController with DocumentSetDeletionComponents {

  object DatabaseStorage extends Storage with DocumentSetDeletionStorage {
    override def findDocumentSetByJob(importJobId: Long) =
      for {
        j <- DocumentSetCreationJobFinder.byDocumentSetCreationJob(importJobId).headOption
        ds <- DocumentSetFinder.byDocumentSet(j.documentSetId).headOption
      } yield ds

    override def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)] =
      DocumentSetCreationJobFinder.all.withDocumentSetsAndOwners.toSeq

  }

  object ApolloJobMessageQueue extends JobMessageQueue with DocumentSetDeletionJobMessageQueue 

  override val storage = DatabaseStorage
  override val jobQueue = ApolloJobMessageQueue
}
