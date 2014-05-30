package controllers

import play.api.mvc.Controller

import org.overviewproject.jobs.models.CancelFileUpload
import org.overviewproject.jobs.models.Delete
import org.overviewproject.jobs.models.DeleteTreeJob
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree }
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.finders.ResultPage

import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.DocumentSetUpdateForm
import controllers.util.DocumentSetDeletionComponents
import models.orm.finders.{ DocumentSetCreationJobFinder, DocumentSetFinder, TreeFinder }
import models.orm.stores.DocumentSetStore

trait DocumentSetController extends Controller {
  import Authorities._

  trait Storage {
    /** Returns a DocumentSet from an ID */
    def findDocumentSet(id: Long): Option[DocumentSet]

    /** Returns all Trees in the given DocumentSets */
    def findTreesByDocumentSets(documentSetIds: Iterable[Long]): Iterable[Tree]

    /** Returns all Trees in the given DocumentSet */
    def findTreesByDocumentSet(documentSetId: Long): Iterable[Tree]

    /** Returns all DocumentSetCreationJobs of failed tree-clustering jobs */
    def findTreeErrorJobsByDocumentSets(documentSetIds: Iterable[Long]): Iterable[DocumentSetCreationJob]

    /** Returns all DocumentSetCreationJobs of failed tree-clustering jobs */
    def findTreeErrorJobsByDocumentSet(documentSetId: Long): Iterable[DocumentSetCreationJob]

    /** Returns a page of DocumentSets */
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet]

    /** Returns all active DocumentSetCreationJobs (job, documentSet, queuePosition) */
    def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    def deleteDocumentSet(documentSet: DocumentSet): Unit

    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob]

  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(deleteJobCommand: DeleteTreeJob): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  protected val indexPageSize = 10

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSetsPage = storage.findDocumentSets(request.user.email, indexPageSize, realPage)
    val documentSets = documentSetsPage.items.toSeq // Squeryl only lets you iterate once

    val trees = storage
      .findTreesByDocumentSets(documentSets.map(_.id))
      .toSeq
      .groupBy(_.documentSetId)

    val treeErrorJobs = storage
      .findTreeErrorJobsByDocumentSets(documentSets.map(_.id))
      .toSeq
      .groupBy(_.documentSetId)

    val documentSetsWithTrees = documentSets.map { ds: DocumentSet =>
      (ds, trees.getOrElse(ds.id, Seq()), treeErrorJobs.getOrElse(ds.id, Seq()))
    }

    val resultPage = ResultPage(documentSetsWithTrees, documentSetsPage.pageDetails)

    val jobs = storage.findDocumentSetCreationJobs(request.user.email).toSeq

    if (resultPage.pageDetails.totalLength == 0 && jobs.length == 0) {
      Redirect(routes.PublicDocumentSetController.index).flashing(flash)
    } else {
      Ok(views.html.DocumentSet.index(request.user, resultPage, jobs))
    }
  }

  def showJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case None => NotFound
      case Some(documentSet) => {
        val trees = storage.findTreesByDocumentSet(id).toSeq
        val treeErrorJobs = storage.findTreeErrorJobsByDocumentSet(id).toSeq
        Ok(views.json.DocumentSet.show(request.user, documentSet, trees, treeErrorJobs))
      }
    }
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    // FIXME: Move all deletion to worker and remove database access here
    // FIXME: Make client distinguish between deleting document sets and canceling jobs

    val documentSet = storage.findDocumentSet(id)
    def onDocumentSet(f: DocumentSet => Unit): Unit =
      documentSet.map(f)

    def done(message: String, event: String) = Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m(message),
      "event" -> event)

    def doneWithError(message: String, event: String) = Redirect(routes.DocumentSetController.index()).flashing(
      "warning" -> m(message),
      "event" -> event)

    // FIXME: If a reclustering job is running, but there are failed jobs, we assume
    // that the delete refers to canceling the running job.
    // It would be better for the client to explicitly tell us what job to cancel, rather
    // than trying to guess.

    // FIXME: gratuitous use of implicit and big if statement should be refactored into a separate class
    implicit val cancelledJob = storage.cancelJob(id) 

    if (noJobCancelled) {
      onDocumentSet(storage.deleteDocumentSet)
      jobQueue.send(Delete(id))
      
      done("deleteDocumentSet.success", "document-set-delete")
    } else if (notStartedTreeJob) {
      jobQueue.send(DeleteTreeJob(id))
      
      done("deleteTree.success", "tree-delete")
    } else if (runningTreeJob) {
      // marking job as cancelled is sufficient
      done("deleteTree.success", "tree-delete")
    } else if (runningInWorker) {
      onDocumentSet(storage.deleteDocumentSet)
      jobQueue.send(Delete(id, waitForJobRemoval = true)) // wait for worker to stop clustering and remove job
      
      done("deleteJob.success", "document-set-delete")
    } else if (notRunning) {
      onDocumentSet(storage.deleteDocumentSet)
      jobQueue.send(Delete(id, waitForJobRemoval = false)) // don't wait for worker
      
      done("deleteJob.success", "document-set-delete")
    } else if (runningInTextExtractionWorker && validTextExtractionJob) {
      jobQueue.send(CancelFileUpload(id, cancelledJob.get.fileGroupId.get))

      done("deleteJob.success", "document-set-delete")
    } else BadRequest // all cases should be covered..
  }

  def update(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    storage.findDocumentSet(id).map { documentSet =>
      DocumentSetUpdateForm(documentSet).bindFromRequest().fold(
        f => BadRequest, { updatedDocumentSet =>
          storage.insertOrUpdateDocumentSet(updatedDocumentSet)
          Ok
        })
    }.getOrElse(NotFound)
  }

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

  val storage: DocumentSetController.Storage
  val jobQueue: DocumentSetController.JobMessageQueue
}

object DocumentSetController extends DocumentSetController with DocumentSetDeletionComponents {
  object DatabaseStorage extends Storage with DocumentSetDeletionStorage {
    override def findDocumentSet(id: Long) = DocumentSetFinder.byDocumentSet(id).headOption
    override def findTreesByDocumentSets(documentSetIds: Iterable[Long]) = TreeFinder.byDocumentSets(documentSetIds)
    override def findTreesByDocumentSet(documentSetId: Long) = TreeFinder.byDocumentSet(documentSetId)

    override def findTreeErrorJobsByDocumentSet(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .byState(DocumentSetCreationJobState.Error)
        .byJobType(DocumentSetCreationJobType.Recluster)
    }

    override def findTreeErrorJobsByDocumentSets(documentSetIds: Iterable[Long]) = {
      DocumentSetCreationJobFinder
        .byDocumentSets(documentSetIds)
        .byState(DocumentSetCreationJobState.Error)
        .byJobType(DocumentSetCreationJobType.Recluster)
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)] = {
      DocumentSetCreationJobFinder
        .byUser(userEmail)
        .excludeFailedTreeCreationJobs
        .excludeCancelledJobs
        .withDocumentSetsAndQueuePositions
        .toSeq
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

  }

  object ApolloJobMessageQueue extends JobMessageQueue with DocumentSetDeletionJobMessageQueue

  override val storage = DatabaseStorage
  override val jobQueue = ApolloJobMessageQueue
}
