package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.{ DocumentSetCreationJobType, Ownership }
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob }
import org.overviewproject.tree.orm.DocumentSetCreationJobState.NotStarted
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.DocumentSetForm.Credentials
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import models.orm.finders.{ DocumentSetCreationJobFinder, DocumentSetFinder, DocumentSetUserFinder }
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.orm.DocumentSetUser 
import models.ResultPage

trait DocumentSetController extends Controller {
  import Authorities._

  trait Storage {
    def findDocumentSet(id: Long): Option[DocumentSet]
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int) : ResultPage[DocumentSet]
    def findDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) : ResultPage[(DocumentSetCreationJob, DocumentSet, Long)]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

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
      case Some(documentSet) => Ok(views.html.DocumentSet.show(request.user, documentSet))
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
    storage.findDocumentSet(id).map(storage.deleteDocumentSet) // ignore not-found
    Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m("delete.success"),
      "event" -> "document-set-delete"
    )
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

  val storage : DocumentSetController.Storage
}

object DocumentSetController extends DocumentSetController {
  object DatabaseStorage extends Storage {
    override def findDocumentSet(id: Long): Option[DocumentSet] = {
      DocumentSetFinder.byDocumentSet(id).headOption
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int) : ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) : ResultPage[(DocumentSetCreationJob, DocumentSet, Long)] = {
      val query = DocumentSetCreationJobFinder.byUser(userEmail).withDocumentSetsAndQueuePositions
      ResultPage(query, pageSize, page)
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

    override def deleteDocumentSet(documentSet: DocumentSet): Unit = {
      DocumentSetStore.deleteOrCancelJob(documentSet)
    }
  }

  override val storage = DatabaseStorage
}
