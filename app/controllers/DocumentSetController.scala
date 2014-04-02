package controllers

import play.api.mvc.Controller
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.DocumentSetUpdateForm
import controllers.util.JobQueueSender
import models.orm.finders.{ DocumentSetCreationJobFinder, DocumentSetFinder, TreeFinder }
import models.orm.stores.DocumentSetStore
import org.overviewproject.jobs.models.{ CancelUploadWithDocumentSet, Delete }
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree }
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.jobs.models.CancelUploadWithDocumentSet

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

    /** Returns type of the job running for the document set, if any exist */
    def findRunningJobType(documentSetId: Long): Option[DocumentSetCreationJobType.Value]

    /** find all jobs for the document set */
    def findAllJobs(documentSetId: Long): Seq[DocumentSetCreationJob]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    def deleteDocumentSet(documentSet: DocumentSet): Unit

    def cancelJob(documentSet: DocumentSet): Unit

    def cancelReclusteringJob(documentSet: DocumentSet, job: DocumentSetCreationJob): Unit
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

    val jobs = storage.findDocumentSetCreationJobs(request.user.email)

    Ok(views.html.DocumentSet.index(request.user, resultPage, jobs))
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

    // FIXME: If a reclustering job is running, but there are failed jobs, we assume
    // that the delete refers to canceling the running job.
    // It would be better for the client to explicitly tell us what job to cancel, rather
    // than trying to guess.
    val jobs = storage.findAllJobs(id)
    val jobsRunningInWorker = jobs.find(j =>
      j.state == DocumentSetCreationJobState.InProgress ||
      j.state == DocumentSetCreationJobState.Preparing ||
        j.state == DocumentSetCreationJobState.Cancelled)

    jobsRunningInWorker.headOption.map { j =>
      j.jobType match {
        case DocumentSetCreationJobType.Recluster => { 
          onDocumentSet(storage.cancelReclusteringJob(_, j))
          done("deleteTree.success", "tree-delete")
        }
        case _ => { 
          onDocumentSet(storage.cancelJob)
          onDocumentSet(storage.deleteDocumentSet)
          if (j.jobType == DocumentSetCreationJobType.FileUpload) JobQueueSender.send(CancelUploadWithDocumentSet(id))
          JobQueueSender.send(Delete(id))
          done("deleteJob.success", "document-set-delete")
        }
      }
    }.getOrElse {
      onDocumentSet(storage.deleteDocumentSet)
      JobQueueSender.send(Delete(id))
      done("deleteDocumentSet.success", "document-set-delete")
    }
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

  val storage: DocumentSetController.Storage
}

object DocumentSetController extends DocumentSetController {
  object DatabaseStorage extends Storage {
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
        .withDocumentSetsAndQueuePositions
        .toSeq
    }

    override def findAllJobs(documentSetId: Long): Seq[DocumentSetCreationJob] =
      DocumentSetCreationJobFinder.byDocumentSet(documentSetId).toSeq

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

    override def deleteDocumentSet(documentSet: DocumentSet): Unit =
      DocumentSetStore.markDeleted(documentSet)

    override def cancelJob(documentSet: DocumentSet): Unit =
      DocumentSetStore.deleteOrCancelJob(documentSet)

    override def cancelReclusteringJob(documentSet: DocumentSet, job: DocumentSetCreationJob): Unit =
      DocumentSetStore.cancelReclusteringJob(documentSet, job)

    override def findRunningJobType(documentSetId: Long) =
      DocumentSetCreationJobFinder.byDocumentSet(documentSetId).headOption.map(_.jobType)

  }

  override val storage = DatabaseStorage
}
