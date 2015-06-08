package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.backend.{DocumentSetBackend,ImportJobBackend,ViewBackend}
import controllers.forms.DocumentSetUpdateForm
import controllers.util.DocumentSetDeletionComponents
import models.orm.finders.{DocumentSetFinder,DocumentSetCreationJobFinder,TagFinder,TreeFinder}
import models.orm.stores.DocumentSetStore
import models.OverviewDatabase
import org.overviewproject.models.DocumentSet
import org.overviewproject.jobs.models.CancelFileUpload
import org.overviewproject.jobs.models.Delete
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.{ DocumentSet=>DeprecatedDocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tag, Tree }
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

    val nViews: Seq[Long] = storage.findNViewsByDocumentSets(documentSets.map(_.id))

    val documentSetsWithCounts = documentSets.zipWithIndex
      .map((t: Tuple2[DeprecatedDocumentSet,Int]) => (t._1, nViews(t._2)))

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
    * * GET /documentsets/123 -&gt; load the first View
    * * GET /documentsets/123/trees/456 -&gt; load a specific View
    *
    * JavaScript will parse the rest of the URL.
    */
  def show(id: Long) = AuthorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        importJobBackend.index(id).flatMap(_.headOption match {
          case None => Future.successful(Ok(views.html.DocumentSet.show(request.user, documentSet.toDeprecatedDocumentSet)))
          case Some(job) => {
            for {
              idsInProcessingOrder <- importJobBackend.indexIdsInProcessingOrder
            } yield {
              val nAheadInQueue = idsInProcessingOrder.indexOf(job.id)
              Ok(views.html.DocumentSet.showProgress(request.user, documentSet, job, math.max(nAheadInQueue, 0)))
            }
          }
        })
      }
    })
  }

  def showWithJsParams(id: Long, jsParams: String) = show(id)

  def showHtmlInJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).map(_ match {
      case None => NotFound
      case Some(documentSet) => OverviewDatabase.inTransaction {
        val nViews = storage.findNViewsByDocumentSets(Seq(id)).head
        Ok(views.json.DocumentSet.showHtml(request.user, documentSet.toDeprecatedDocumentSet, nViews))
      }
    })
  }

  def showJson(id: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(id)).async {
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => OverviewDatabase.inTransaction {
        val trees = storage.findTrees(id).map(_.copy()).toArray
        val viewJobs = storage.findViewJobs(id).map(_.copy()).toArray
        val tags = storage.findTags(id).map(_.copy()).toArray

        for {
          _views <- viewBackend.index(id)
        } yield Ok(views.json.DocumentSet.show(
          documentSet.toDeprecatedDocumentSet,
          trees,
          _views,
          viewJobs,
          tags
        ))
      }
    })
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    backend.show(id).map(_ match {
      case None => NotFound
      case Some(documentSet) => _delete(documentSet)
    })
  }

  private def _delete(documentSet: DocumentSet) = OverviewDatabase.inTransaction {
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    // FIXME: Move all deletion to worker and remove database access here
    // FIXME: Make client distinguish between deleting document sets and canceling jobs

    def done(message: String, event: String) = Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m(message),
      "event" -> event
    )

    // FIXME: If a reclustering job is running, but there are failed jobs, we assume
    // that the delete refers to canceling the running job.
    // It would be better for the client to explicitly tell us what job to cancel, rather
    // than trying to guess.

    // FIXME: gratuitous use of implicit and big if statement should be refactored into a separate class
    implicit val cancelledJob: Option[DocumentSetCreationJob] = storage.cancelJob(documentSet.id)

    if (noJobCancelled) {
      storage.deleteDocumentSet(documentSet.toDeprecatedDocumentSet)
      jobQueue.send(Delete(documentSet.id))
      
      done("deleteDocumentSet.success", "document-set-delete")
    } else if (runningInWorker) {
      storage.deleteDocumentSet(documentSet.toDeprecatedDocumentSet)
      jobQueue.send(Delete(documentSet.id, waitForJobRemoval = true)) // wait for worker to stop clustering and remove job
      
      done("deleteJob.success", "document-set-delete")
    } else if (notRunning) {
      storage.deleteDocumentSet(documentSet.toDeprecatedDocumentSet)
      jobQueue.send(Delete(documentSet.id, waitForJobRemoval = false)) // don't wait for worker
      
      done("deleteJob.success", "document-set-delete")
    } else if (runningInTextExtractionWorker && validTextExtractionJob) {
      jobQueue.send(CancelFileUpload(documentSet.id, cancelledJob.get.fileGroupId.get))

      done("deleteJob.success", "document-set-delete")
    } else BadRequest // all cases should be covered..
  }

  def update(id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    backend.show(id).map(_ match {
      case None => NotFound
      case Some(documentSet) => {
        DocumentSetUpdateForm(documentSet).bindFromRequest().fold(
          f => BadRequest,
          updatedDocumentSet => OverviewDatabase.inTransaction {
            storage.insertOrUpdateDocumentSet(updatedDocumentSet)
            NoContent
          }
        )
      }
    })
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
  protected val backend: DocumentSetBackend
  protected val importJobBackend: ImportJobBackend
  protected val viewBackend: ViewBackend
}

object DocumentSetController extends DocumentSetController with DocumentSetDeletionComponents {
  trait Storage {
    /** Returns a Seq: for each DocumentSet, the total number of Views, Trees
      * and Recluster jobs.
      *
      * The return value comes in the same order as the input parameter.
      */
    def findNViewsByDocumentSets(documentSetIds: Seq[Long]): Seq[Long]

    /** Returns a page of DocumentSets */
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DeprecatedDocumentSet]

    /** Returns all active DocumentSetCreationJobs (job, documentSet, queuePosition) */
    def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DeprecatedDocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): Unit

    def deleteDocumentSet(documentSet: DeprecatedDocumentSet): Unit

    def cancelJob(documentSetId: Long): Option[DocumentSetCreationJob]

    /** All Views for the document set. */
    def findTrees(documentSetId: Long) : Iterable[Tree]

    /** All View-creation jobs for the document set. */
    def findViewJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]

    /** All Tags for the document set. */
    def findTags(documentSetId: Long) : Iterable[Tag]
  }

  trait JobMessageQueue {
    def send(deleteCommand: Delete): Unit
    def send(cancelFileUploadCommand: CancelFileUpload): Unit
  }

  object DatabaseStorage extends Storage with DocumentSetDeletionStorage with org.overviewproject.database.BlockingDatabaseProvider {
    override def findNViewsByDocumentSets(documentSetIds: Seq[Long]) = {
      if (documentSetIds.isEmpty) {
        Seq()
      } else {
        import blockingDatabaseApi._
        import slick.jdbc.GetResult

        // TODO get rid of Trees and DocumentSetCreationJobs. Then Slick queries
        // would make more sense than straight SQL.
        val documentSetIdToCount: Map[Long,Long] = blockingDatabase.run(sql"""
          WITH ids AS (
            SELECT *
            FROM (VALUES #${documentSetIds.map("(" + _ + ")").mkString(",")}) AS t(id)
          ), counts1 AS (
            SELECT document_set_id, COUNT(*) AS c
            FROM document_set_creation_job
            WHERE document_set_id IN (SELECT id FROM ids)
              AND state <> #${DocumentSetCreationJobState.Cancelled.id}
            GROUP BY document_set_id
          ), counts2 AS (
            SELECT document_set_id, COUNT(*) AS c
            FROM tree
            WHERE document_set_id IN (SELECT id FROM ids)
            GROUP BY document_set_id
          ), counts3 AS (
            SELECT document_set_id, COUNT(*) AS c
            FROM "view"
            WHERE document_set_id IN (SELECT id FROM ids)
            GROUP BY document_set_id
          ), all_counts AS (
            SELECT * FROM counts1
            UNION
            SELECT * FROM counts2
            UNION
            SELECT * FROM counts3
          )
          SELECT document_set_id, SUM(c)
          FROM all_counts
          GROUP BY document_set_id
        """.as[(Long,Long)])
          .toMap

        documentSetIds.map((id) => documentSetIdToCount.getOrElse(id, 0L))
      }
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DeprecatedDocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DeprecatedDocumentSet, Long)] = {
      DocumentSetCreationJobFinder
        .byUser(userEmail)
        .forUpdate // See comment in index(). Should be "forShare" but Squeryl doesn't support that
        .excludeTreeCreationJobs
        .excludeCancelledJobs
        .withDocumentSetsAndQueuePositions
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet) = {
      DocumentSetStore.insertOrUpdate(documentSet.toDeprecatedDocumentSet)
    }

    override def findTrees(documentSetId: Long) = {
      TreeFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findViewJobs(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .excludeCancelledJobs
        .toSeq
    }

    override def findTags(documentSetId: Long) = {
      TagFinder.byDocumentSet(documentSetId).toSeq
    }
  }

  object ApolloJobMessageQueue extends JobMessageQueue with DocumentSetDeletionJobMessageQueue

  override val storage = DatabaseStorage
  override val jobQueue = ApolloJobMessageQueue
  override val backend = DocumentSetBackend
  override val importJobBackend = ImportJobBackend
  override val viewBackend = ViewBackend
}
