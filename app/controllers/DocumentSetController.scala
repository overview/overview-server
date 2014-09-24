package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.backend.VizBackend
import controllers.forms.DocumentSetUpdateForm
import controllers.util.DocumentSetDeletionComponents
import models.orm.finders.{DocumentSetFinder,DocumentSetCreationJobFinder,SearchResultFinder,TagFinder,TreeFinder}
import models.orm.stores.DocumentSetStore
import org.overviewproject.jobs.models.CancelFileUpload
import org.overviewproject.jobs.models.Delete
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tag, Tree, SearchResult }
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.finders.ResultPage

trait DocumentSetController extends Controller {
  import Authorities._

  protected val indexPageSize = 10

  def index(page: Int) = AuthorizedAction.inTransaction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page

    /*
     * Find the DocumentSetCreationJobs first, and lock them.
     *
     * The only difference between a DocumentSet and a DocumentSetCreationJob
     * is that a DocumentSet is _missing_ a DocumentSetCreationJob. We want to
     * find all DocumentSets _with_ and all DocumentSets _without_, in two
     * separate queries, so we need to avoid races. The only races that can
     * happen are when jobs are deleted. So if we prevent the jobs from being
     * deleted during this transaction, there's no more race.
     */
    val jobs = storage.findDocumentSetCreationJobs(request.user.email).toSeq

    val documentSetsPage = storage.findDocumentSets(request.user.email, indexPageSize, realPage)
    val documentSets = documentSetsPage.items.toSeq // Squeryl only lets you iterate once

    val nVizs = storage.findNTreesByDocumentSets(documentSets.map(_.id))
    val nJobs = storage.findNJobsByDocumentSets(documentSets.map(_.id))

    val documentSetsWithCounts = documentSets.zipWithIndex
      .map((t: Tuple2[DocumentSet,Int]) => (t._1, nVizs(t._2), nJobs(t._2)))

    val resultPage = ResultPage(documentSetsWithCounts, documentSetsPage.pageDetails)

    if (resultPage.pageDetails.totalLength == 0 && jobs.length == 0) {
      Redirect(routes.PublicDocumentSetController.index).flashing(request.flash)
    } else {
      Ok(views.html.DocumentSet.index(request.user, resultPage, jobs))
    }
  }

  /** Shows a DocumentSet to the user.
    *
    * This is a shell of a page with a giant JavaScript application inside it.
    *
    * Ignore the silly "jsParams" argument. It's so users can bookmark URLs.
    *
    * * GET /documentsets/123 -&gt; load the first Viz
    * * GET /documentsets/123/trees/456 -&gt; load a specific Viz
    *
    * JavaScript will parse the rest of the URL.
    */
  def show(id: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case None => NotFound
      case Some(documentSet) => {
        val isSearchable = storage.isDocumentSetSearchable(documentSet)
        Ok(views.html.DocumentSet.show(request.user, documentSet, isSearchable))
      }
    }
  }

  def showWithJsParams(id: Long, jsParams: String) = show(id)

  def showHtmlInJson(id: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case None => NotFound
      case Some(documentSet) => {
        val nTrees = storage.findNTreesByDocumentSets(Seq(id)).headOption.getOrElse(0)
        val nJobs = storage.findNJobsByDocumentSets(Seq(id)).headOption.getOrElse(0)
        Ok(views.json.DocumentSet.showHtml(request.user, documentSet, nTrees, nJobs))
      }
    }
  }

  def showJson(id: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(id)).async {
    storage.findDocumentSet(id).map(_.copy()) match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        val trees = storage.findTrees(id).map(_.copy()).toArray
        val vizJobs = storage.findVizJobs(id).map(_.copy()).toArray
        val tags = storage.findTags(id).map(_.copy()).toArray
        val searchResults = storage.findSearchResults(id).map(_.copy()).toArray

        for {
          vizs <- vizBackend.index(id)
        } yield Ok(views.json.DocumentSet.show(
          documentSet,
          trees,
          vizs,
          vizJobs,
          tags,
          searchResults
        ))
      }
    }
  }

  def delete(id: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(id)) { implicit request =>
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

  def update(id: Long) = AuthorizedAction.inTransaction(adminUser) { implicit request =>
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
      
  private def validTextExtractionJob(implicit job: Option[DocumentSetCreationJob]): Boolean = 
    jobTest { j => j.fileGroupId.isDefined }
  
  private def runningInWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.jobType != Recluster && j.state == InProgress }

  private def notRunning(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == NotStarted || j.state == Error || j.state == Cancelled }

  private def runningInTextExtractionWorker(implicit job: Option[DocumentSetCreationJob]): Boolean =
    jobTest { j => j.state == FilesUploaded || j.state == TextExtractionInProgress }

  protected val storage: DocumentSetController.Storage
  protected val jobQueue: DocumentSetController.JobMessageQueue
  protected val vizBackend: VizBackend
}

object DocumentSetController extends DocumentSetController with DocumentSetDeletionComponents {
  trait Storage {
    /** Returns a DocumentSet from an ID */
    def findDocumentSet(id: Long): Option[DocumentSet]

    /** Returns a Seq: for each DocumentSet, the number of Trees.
      *
      * The return value comes in the same order as the input parameter.
      */
    def findNTreesByDocumentSets(documentSetIds: Seq[Long]): Seq[Int]

    /** Returns a Seq: for each DocumentSet, the number of Recluster jobs.
      *
      * The return value comes in the same order as the input parameter.
      */
    def findNJobsByDocumentSets(documentSetIds: Seq[Long]): Seq[Int]

    /** Returns a page of DocumentSets */
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet]

    /** Returns all active DocumentSetCreationJobs (job, documentSet, queuePosition) */
    def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    def deleteDocumentSet(documentSet: DocumentSet): Unit

    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob]

    /** All Vizs for the document set. */
    def findTrees(documentSetId: Long) : Iterable[Tree]

    /** All Viz-creation jobs for the document set. */
    def findVizJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]

    /** All Tags for the document set. */
    def findTags(documentSetId: Long) : Iterable[Tag]

    /** All SearchResults for the document set. */
    def findSearchResults(documentSetId: Long) : Iterable[SearchResult]

    /** Returns true iff we can search the document set.
      *
      * This is a '''hack'''. All document sets ''should'' be searchable, but
      * document sets imported before indexing was implemented are not.
      */
    def isDocumentSetSearchable(documentSet: DocumentSet): Boolean
  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  object DatabaseStorage extends Storage with DocumentSetDeletionStorage {
    private val FirstSearchableDocumentSetVersion = 2

    override def findDocumentSet(id: Long) = DocumentSetFinder.byDocumentSet(id).headOption

    override def findNTreesByDocumentSets(documentSetIds: Seq[Long]) = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      val idToNTrees = from(models.orm.Schema.trees)(t =>
        where(t.documentSetId in documentSetIds)
        groupBy(t.documentSetId)
        compute(count(t.id))
      )
        .toSeq
        .map((g) => (g.key -> g.measures.toInt))
        .toMap

      documentSetIds.map((id) => idToNTrees.getOrElse(id, 0))
    }

    override def findNJobsByDocumentSets(documentSetIds: Seq[Long]) = {
      import org.overviewproject.postgres.SquerylEntrypoint._

      val idToNTrees = from(models.orm.Schema.documentSetCreationJobs)(j =>
        where(j.documentSetId in documentSetIds and j.state <> DocumentSetCreationJobState.Cancelled)
        groupBy(j.documentSetId)
        compute(count(j.id))
      )
        .toSeq
        .map((g) => (g.key -> g.measures.toInt))
        .toMap

      documentSetIds.map((id) => idToNTrees.getOrElse(id, 0))
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)] = {
      DocumentSetCreationJobFinder
        .byUser(userEmail)
        .forUpdate // See comment in index(). Should be "forShare" but Squeryl doesn't support that
        .excludeTreeCreationJobs
        .excludeCancelledJobs
        .withDocumentSetsAndQueuePositions
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

    override def findTrees(documentSetId: Long) = {
      TreeFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findVizJobs(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .excludeCancelledJobs
        .toSeq
    }

    override def findTags(documentSetId: Long) = {
      TagFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findSearchResults(documentSetId: Long) = {
      SearchResultFinder
        .byDocumentSet(documentSetId)
        .onlyNewest
    }

    override def isDocumentSetSearchable(documentSet: DocumentSet) = documentSet.version >= FirstSearchableDocumentSetVersion
  }

  object ApolloJobMessageQueue extends JobMessageQueue with DocumentSetDeletionJobMessageQueue

  override val storage = DatabaseStorage
  override val jobQueue = ApolloJobMessageQueue
  override val vizBackend = VizBackend
}
