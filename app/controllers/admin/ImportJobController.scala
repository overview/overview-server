package controllers.admin

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.stores.DocumentSetStore
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSet
import models.orm.User
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import models.orm.finders.DocumentSetFinder
import org.overviewproject.jobs.models.{ CancelFileUpload, Delete, DeleteTreeJob }
import controllers.util.JobQueueSender

trait ImportJobController extends Controller {

  trait Storage {
    def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)]
    def findDocumentSetByJob(jobId: Long): Option[DocumentSet]
    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob]
    def deleteDocumentSet(documentSet: DocumentSet): Unit
  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(deleteJobCommand: DeleteTreeJob): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  val storage: Storage
  val jobQueue: JobMessageQueue

  def index() = AuthorizedAction(adminUser) { implicit request =>
    val jobs = storage.findAllDocumentSetCreationJobs

    Ok(views.html.admin.ImportJob.index(request.user, jobs))
  }

  def delete(importJobId: Long) = AuthorizedAction(adminUser) { implicit request =>

    val documentSet = storage.findDocumentSetByJob(importJobId)
    def onDocumentSet[A](f: DocumentSet => A): Option[A] =
      documentSet.map(f)

    implicit val cancelledJob = onDocumentSet(ds => storage.cancelJob(ds.id)).flatten
    def id = documentSet.get.id

    if (notStartedTreeJob) {
      jobQueue.send(DeleteTreeJob(id))
      flashSuccess
    } else if (runningTreeJob) {
      flashSuccess
    } else if (runningInWorker) {
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

  private def jobTest(test: DocumentSetCreationJob => Boolean)(implicit job: Option[DocumentSetCreationJob]): Boolean =
    job.map(test)
      .getOrElse(false)

  private def noJobCancelled(implicit job: Option[DocumentSetCreationJob]): Boolean = job.isEmpty

  private def notStartedTreeJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => (j.jobType == Recluster && j.state == NotStarted) }

  private def validTextExtractionJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.fileGroupId.isDefined }

  private def runningTreeJob(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.jobType == Recluster && j.state != NotStarted }

  private def runningInWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.jobType != Recluster && j.state == InProgress }

  private def notRunning(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == NotStarted || j.state == Error || j.state == Cancelled }

  private def runningInTextExtractionWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == FilesUploaded || j.state == TextExtractionInProgress }

}

object ImportJobController extends ImportJobController {

  object DatabaseStorage extends Storage {
    override def findDocumentSetByJob(importJobId: Long) =
      for {
        j <- DocumentSetCreationJobFinder.byDocumentSetCreationJob(importJobId).headOption
        ds <- DocumentSetFinder.byDocumentSet(j.documentSetId).headOption
      } yield ds

    override def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)] =
      DocumentSetCreationJobFinder.all.withDocumentSetsAndOwners.toSeq

    override def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob] =
      DocumentSetCreationJobStore.findCancellableJobByDocumentSetAndCancel(documentSetId)

    override def deleteDocumentSet(documentSet: DocumentSet): Unit =
      DocumentSetStore.markDeleted(documentSet)

  }

  object ApolloJobMessageQueue extends JobMessageQueue {
    override def send(deleteCommand: Delete): Unit = JobQueueSender.send(deleteCommand)
    override def send(deleteJobCommand: DeleteTreeJob): Unit = JobQueueSender.send(deleteJobCommand)
    override def send(cancelFileUploadCommand: CancelFileUpload): Unit = JobQueueSender.send(cancelFileUploadCommand)
  }

  override val storage = DatabaseStorage
  override val jobQueue = ApolloJobMessageQueue
}