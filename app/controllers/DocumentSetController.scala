package controllers

import play.api.mvc.Controller
import org.overviewproject.jobs.models.Delete
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob }
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import controllers.util.JobQueueSender
import models.ResultPage
import models.orm.finders.{DocumentSetCreationJobFinder, DocumentSetFinder}
import models.orm.stores.DocumentSetStore
import org.overviewproject.jobs.models.CancelUploadWithDocumentSet



trait DocumentSetController extends Controller {
  import Authorities._

  trait Storage {
    def findDocumentSet(id: Long): Option[DocumentSet]
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet]
    def findDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int): ResultPage[(DocumentSetCreationJob, DocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    /**
     * Returns true iff we can search the document set.
     *
     * This is a '''hack'''. All document sets ''should'' be searchable, but
     * document sets imported before indexing was implemented are not.
     */
    def isDocumentSetSearchable(documentSet: DocumentSet): Boolean

    def deleteDocumentSet(documentSet: DocumentSet): Unit
  }

  private val form = DocumentSetForm()
  private val pageSize = 10
  private val jobPageSize = 50 // show them "all", but don't crash if something's wrong

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSets = storage.findDocumentSets(request.user.email, pageSize, realPage)
    val jobs = storage.findDocumentSetCreationJobs(request.user.email, jobPageSize, 1)

    Ok(views.html.DocumentSet.index(request.user, documentSets, jobs, form))
  }

  def show(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case Some(documentSet) =>
        Ok(views.html.DocumentSet.show(
          request.user,
          documentSet,
          storage.isDocumentSetSearchable(documentSet)))
      case None => NotFound
    }
  }

  def showJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case Some(documentSet) => Ok(views.json.DocumentSet.show(request.user, documentSet))
      case None => NotFound
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
  private val FirstSearchableDocumentSetVersion = 2

  object DatabaseStorage extends Storage {
    override def findDocumentSet(id: Long): Option[DocumentSet] = {
      DocumentSetFinder.byDocumentSet(id).headOption
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int): ResultPage[(DocumentSetCreationJob, DocumentSet, Long)] = {
      val query = DocumentSetCreationJobFinder.byUser(userEmail).withDocumentSetsAndQueuePositions
      ResultPage(query, pageSize, page)
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }


    override def isDocumentSetSearchable(documentSet: DocumentSet): Boolean = documentSet.version >= FirstSearchableDocumentSetVersion

    override def deleteDocumentSet(documentSet: DocumentSet): Unit = {
      DocumentSetStore.deleteOrCancelJob(documentSet)
      DocumentSetStore.markDeleted(documentSet)
    }
  }

  override val storage = DatabaseStorage
}
