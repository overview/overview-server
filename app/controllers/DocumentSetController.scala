package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsError,JsObject,JsResult,JsSuccess}
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import com.overviewdocs.database.{DeprecatedDatabase,HasBlockingDatabase}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,ImportJob}
import com.overviewdocs.models.tables.DocumentSets
import com.overviewdocs.tree.orm.{DocumentSetCreationJob=>DeprecatedDocumentSetCreationJob,Tag,Tree}
import com.overviewdocs.tree.orm.DocumentSetCreationJobState
import com.overviewdocs.tree.DocumentSetCreationJobType
import controllers.auth.{AuthorizedAction,Authorities}
import controllers.backend.{DocumentSetBackend,ImportJobBackend,ViewBackend}
import controllers.forms.DocumentSetUpdateForm
import controllers.util.JobQueueSender
import models.orm.finders.{DocumentSetCreationJobFinder,TagFinder,TreeFinder}
import models.orm.stores.DocumentSetCreationJobStore
import models.pagination.{Page,PageRequest}

trait DocumentSetController extends Controller {
  import Authorities._

  protected val indexPageSize = 10

  def index(page: Int) = AuthorizedAction(anyUser).async { implicit request =>
    val requestedPage: Int = RequestData(request).getInt("page").getOrElse(0)
    val realPage = if (requestedPage <= 0) 1 else requestedPage
    val pageRequest = PageRequest((realPage - 1) * indexPageSize, indexPageSize)

    /*
     * There is a race if a DocumentSetCreationJob disappears between queries.
     * We prefer to display the DocumentSet twice in that case, rather than
     * zero times. (Slick doesn't support "FOR UPDATE" yet. This race is rare:
     * it used to be common for brief jobs when we redirected users to the
     * index page upon job creation; now we redirect them to the show page so
     * there are usually only long-running jobs or no jobs on the index page.)
     */
    for {
      jobs: Iterable[ImportJob] <- importJobBackend.indexByUser(request.user.email)
      documentSets: Page[DocumentSet] <- backend.indexPageByOwner(request.user.email, pageRequest)
    } yield {
      if (documentSets.pageInfo.total == 0) {
        Redirect(routes.PublicDocumentSetController.index).flashing(request.flash)
      } else {
        val jobsById: Map[Long,Iterable[ImportJob]] = jobs.groupBy(_.documentSetId)
        val nViewsById: Map[Long,Int] = storage.findNViewsByDocumentSets(documentSets.items.map(_.id))
        val detailedDocumentSets: Page[(DocumentSet,Iterable[ImportJob],Int)] = documentSets.map { documentSet =>
          (
            documentSet,
            jobsById.getOrElse(documentSet.id, Iterable()),
            nViewsById.getOrElse(documentSet.id, 0)
          )
        }
        Ok(views.html.DocumentSet.index(request.user, detailedDocumentSets))
      }
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
        importJobBackend.indexByDocumentSet(id).map(_ match {
          case Seq() => Ok(views.html.DocumentSet.show(request.user, documentSet))
          case _ => Ok(views.html.DocumentSet.showProgress(request.user, documentSet))
        })
      }
    })
  }

  def showWithJsParams(id: Long, jsParams: String) = show(id)

  def showHtmlInJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        for {
          jobs: Iterable[ImportJob] <- importJobBackend.indexByDocumentSet(documentSet.id)
          nViews: Int <- Future.successful(storage.findNViewsByDocumentSets(Seq(id)).get(id).getOrElse(0))
        } yield Ok(views.json.DocumentSet.showHtml(documentSet, jobs, nViews))
      }
    })
  }

  def showJson(id: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(id)).async {
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => DeprecatedDatabase.inTransaction {
        val trees = storage.findTrees(id).map(_.copy()).toArray
        val viewJobs = storage.findViewJobs(id).map(_.copy()).toArray
        val tags = storage.findTags(id).map(_.copy()).toArray

        for {
          _views <- viewBackend.index(id)
        } yield Ok(views.json.DocumentSet.show(
          documentSet,
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
      case Some(documentSet) => _delete(documentSet.id)
    })
  }

  private def _delete(documentSetId: Long) = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    // FIXME: Move all deletion to worker and remove database access here
    // FIXME: Make client distinguish between deleting document sets and canceling jobs

    def done(message: String) = Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m(message),
      "event" -> "document-set-delete"
    )

    implicit class MaybeCancelledJob(maybeJob: Option[DeprecatedDocumentSetCreationJob]) {
      def doesNotExist: Boolean = maybeJob.isEmpty
      def wasRunningInWorker: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.InProgress)
        && maybeJob.map(_.jobType) != Some(DocumentSetCreationJobType.Recluster)
      )
      def wasNotRunning: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.NotStarted)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.Error)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.Cancelled)
      )
      def wasRunningInTextExtractionWorker: Boolean = (
        maybeJob.map(_.state) == Some(DocumentSetCreationJobState.FilesUploaded)
        || maybeJob.map(_.state) == Some(DocumentSetCreationJobState.TextExtractionInProgress)
      )
    }

    // FIXME: If a reclustering job is running, but there are failed jobs, we assume
    // that the delete refers to canceling the running job.
    // It would be better for the client to explicitly tell us what job to cancel, rather
    // than trying to guess.
    val cancelledJob: Option[DeprecatedDocumentSetCreationJob] = storage.cancelJob(documentSetId)

    if (cancelledJob.doesNotExist || cancelledJob.wasRunningInWorker || cancelledJob.wasNotRunning) {
      storage.deleteDocumentSet(documentSetId)
      jobQueue.send(DocumentSetCommands.DeleteDocumentSet(documentSetId))
      done("deleteJob.success")
    } else if (cancelledJob.wasNotRunning) {
      storage.deleteDocumentSet(documentSetId)
      jobQueue.send(DocumentSetCommands.DeleteDocumentSet(documentSetId))
      done("deleteJob.success")
    } else {
      throw new RuntimeException("A job was in a state we do not handle")
    }
  }

  def update(id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    backend.show(id).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        DocumentSetUpdateForm(documentSet).bindFromRequest().fold(
          f => Future.successful(BadRequest),
          hackyDocumentSet => backend.updatePublic(id, hackyDocumentSet.public).map(_ => NoContent)
        )
      }
    })
  }

  def updateJson(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    // The interface is complicated enough that we should create error messages
    // ourselves.
    //
    // One error if metadataSchema is not *set*; another error if it is not
    // *valid*.
    val maybeMetadataSchema: Option[JsResult[MetadataSchema]] = for {
      jsonBody <- request.body.asJson
      jsonBodyAsObject <- jsonBody.asOpt[JsObject]
      metadataSchemaJson <- jsonBodyAsObject.value.get("metadataSchema")
    } yield metadataSchemaJson.validate[MetadataSchema](MetadataSchema.Json.reads)

    def err(code: String, message: String) = Future.successful(BadRequest(jsonError(code, message)))

    maybeMetadataSchema match {
      case None => err("illegal-arguments", "You must specify a metadataSchema property in the JSON body")
      case Some(JsError(_)) => err(
        "illegal-arguments",
        """metadataSchema should look like { "version": 1, "fields": [ { "name": "foo", "type": "String" } ] }"""
      )
      case Some(JsSuccess(metadataSchema, _)) => backend.updateMetadataSchema(id, metadataSchema).map(_ => NoContent)
    }
  }

  protected val storage: DocumentSetController.Storage
  protected val jobQueue: JobQueueSender
  protected val backend: DocumentSetBackend
  protected val importJobBackend: ImportJobBackend
  protected val viewBackend: ViewBackend
}

object DocumentSetController extends DocumentSetController {
  trait Storage {
    /** Returns a mapping from DocumentSet ID to the total number of Views,
      * Trees and Recluster jobs.
      */
    def findNViewsByDocumentSets(documentSetIds: Seq[Long]): Map[Long,Int]

    def deleteDocumentSet(documentSetId: Long): Unit

    def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob]

    /** All Views for the document set. */
    def findTrees(documentSetId: Long) : Iterable[Tree]

    /** All View-creation jobs for the document set. */
    def findViewJobs(documentSetId: Long) : Iterable[DeprecatedDocumentSetCreationJob]

    /** All Tags for the document set. */
    def findTags(documentSetId: Long) : Iterable[Tag]
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def cancelJob(documentSetId: Long): Option[DeprecatedDocumentSetCreationJob] = {
      DeprecatedDatabase.inTransaction {
        DocumentSetCreationJobStore.findCancellableJobByDocumentSetAndCancel(documentSetId)
      }
    }

    override def deleteDocumentSet(documentSetId: Long) = {
      blockingDatabase.runUnit(
        DocumentSets
          .filter(_.id === documentSetId)
          .map(_.deleted).update(true)
      )
    }

    override def findNViewsByDocumentSets(documentSetIds: Seq[Long]) = {
      if (documentSetIds.isEmpty) {
        Map()
      } else {
        import database.api._
        import slick.jdbc.GetResult

        // TODO get rid of Trees and DocumentSetCreationJobs. Then Slick queries
        // would make more sense than straight SQL.
        blockingDatabase.run(sql"""
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
        """.as[(Long,Int)])
          .toMap
      }
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

  override val storage = DatabaseStorage
  override val jobQueue = JobQueueSender
  override val backend = DocumentSetBackend
  override val importJobBackend = ImportJobBackend
  override val viewBackend = ViewBackend
}
