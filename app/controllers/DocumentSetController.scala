package controllers

import play.api.mvc.Controller

import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import controllers.util.JobQueueSender
import models.orm.finders.{DocumentSetCreationJobFinder, DocumentSetFinder, TreeFinder}
import models.orm.stores.DocumentSetStore
import models.ResultPage
import org.overviewproject.jobs.models.{CancelUploadWithDocumentSet,Delete}
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, Tree }

trait DocumentSetController extends Controller {
  import Authorities._

  trait Storage {
    /** Returns a DocumentSet from an ID */
    def findDocumentSet(id: Long): Option[DocumentSet]

    /** Returns all Trees in the given DocumentSets */
    def findTreesByDocumentSets(documentSetIds: Iterable[Long]) : Iterable[Tree]

    /** Returns all Trees in the given DocumentSet */
    def findTreesByDocumentSet(documentSetId: Long) : Iterable[Tree]

    /** Returns a page of DocumentSets */
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet]

    /** Returns all active DocumentSetCreationJobs (job, documentSet, queuePosition) */
    def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    def deleteDocumentSet(documentSet: DocumentSet): Unit
  }

  private val form = DocumentSetForm()
  protected val indexPageSize = 10

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSetsPage = storage.findDocumentSets(request.user.email, indexPageSize, realPage)
    val documentSets = documentSetsPage.items.toSeq // Squeryl only lets you iterate once
    val trees = storage
      .findTreesByDocumentSets(documentSets.map(_.id))
      .toSeq
      .groupBy(_.documentSetId)

    val documentSetsWithTrees = documentSets.map { ds: DocumentSet => (ds -> trees.getOrElse(ds.id, Seq())) }

    val resultPage = ResultPage(documentSetsWithTrees, documentSetsPage.pageDetails)

    val jobs = storage.findDocumentSetCreationJobs(request.user.email)

    Ok(views.html.DocumentSet.index(request.user, resultPage, jobs, form))
  }

  def showJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case None => NotFound
      case Some(documentSet) => {
        val trees = storage.findTreesByDocumentSet(id).toSeq
        Ok(views.json.DocumentSet.show(request.user, documentSet, trees))
      }
    }
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    
    // FIXME: Move all deletion to worker and remove database access here
    storage.findDocumentSet(id).map(storage.deleteDocumentSet) // ignore not-found
    JobQueueSender.send(CancelUploadWithDocumentSet(id))
    JobQueueSender.send(Delete(id))
    Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m("delete.success"),
      "event" -> "document-set-delete")
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

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String): Iterable[(DocumentSetCreationJob, DocumentSet, Long)] = {
      DocumentSetCreationJobFinder
        .byUser(userEmail)
        .withDocumentSetsAndQueuePositions
        .toSeq
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

    override def deleteDocumentSet(documentSet: DocumentSet): Unit = {
      DocumentSetStore.deleteOrCancelJob(documentSet)
      DocumentSetStore.markDeleted(documentSet)
    }
  }

  override val storage = DatabaseStorage
}
