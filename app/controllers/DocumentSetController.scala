package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.Ownership
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.DocumentSetForm.Credentials
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import controllers.forms.UserRoleForm
import models.orm.finders.DocumentSetFinder
import models.orm.{ DocumentSet, User, DocumentSetUser }
import models.{ OverviewDocumentSet, OverviewDocumentSetCreationJob, ResultPage }

trait DocumentSetController extends Controller {
  import Authorities._

  private val form = DocumentSetForm()
  private val pageSize = 10
  private val jobPageSize = 50 // show them "all", but don't crash if something's wrong

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSets = loadDocumentSets(request.user.email, pageSize, realPage)
    val jobs = loadDocumentSetCreationJobs(request.user.email, pageSize, 1)

    Ok(views.html.DocumentSet.index(request.user, documentSets, jobs, form))
  }

  def show(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    val documentSet = OverviewDocumentSet.findById(id)
    documentSet match {
      case Some(ds) => Ok(views.html.DocumentSet.show(request.user, ds))
      case None => NotFound
    }
  }

  def showJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    OverviewDocumentSet.findById(id) match {
      case Some(ds) => Ok(views.json.DocumentSet.show(request.user, ds))
      case None => NotFound
    }
  }

  def create() = AuthorizedAction(anyUser) { implicit request =>
    form.bindFromRequest().fold(
      f => index(1)(request),
      (tuple) => {
        val documentSet = tuple._1
        val credentials = tuple._2

        val saved = saveDocumentSet(documentSet)
        setDocumentSetUserRole(saved, request.user.email, Ownership.Owner)
        createDocumentSetCreationJob(saved, credentials)

        Redirect(routes.DocumentSetController.index()).flashing(
          "event" -> "document-set-create"
        )
      })
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    OverviewDocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m("delete.success"),
      "event" -> "document-set-delete"
    )
  }

  def update(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    val documentSet = loadDocumentSet(id)
    documentSet.map { d =>
      DocumentSetUpdateForm(d).bindFromRequest().fold(
        f => BadRequest, { updatedDocumentSet =>
          saveDocumentSet(updatedDocumentSet)
          Ok
        })
    }.getOrElse(NotFound)
  }

  def createClone(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    val cloneStatus = loadDocumentSet(id).map { d =>
      OverviewDocumentSet(d).cloneForUser(request.user.id)
      Seq(
        "event" -> "document-set-create-clone"
      )
    }.getOrElse(
      Seq(
        "error" -> m("clone.failure")
      )
    )
    Redirect(routes.DocumentSetController.index()).flashing(cloneStatus : _*)
  }

  def showUsers(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val viewers = loadDocumentSetViewers(id)
    Ok(views.json.DocumentSetUser.showUsers(viewers))
  }

  def addUser(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    loadDocumentSet(id).map { ds =>
      UserRoleForm(id).bindFromRequest().fold(
        f => BadRequest, { dsu =>
          setDocumentSetUserRole(ds, dsu.userEmail, dsu.role) // Currently only sets viewer role
          Ok
        })
    }.getOrElse(NotFound)
  }

  def removeUser(id: Long, email: String) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    loadDocumentSet(id).map { ds =>
      removeDocumentSetViewer(ds, email)
      Ok
    }.getOrElse(NotFound)
  }

  protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int)
    : ResultPage[(OverviewDocumentSetCreationJob, OverviewDocumentSet)]
  protected def loadDocumentSets(userEmail: String, pageSize: Int, page: Int) : ResultPage[OverviewDocumentSet]
  protected def loadDocumentSet(id: Long): Option[DocumentSet]
  protected def saveDocumentSet(documentSet: DocumentSet): DocumentSet
  protected def setDocumentSetUserRole(documentSet: DocumentSet, email: String, role: Ownership.Value)
  protected def removeDocumentSetViewer(documentSet: DocumentSet, email: String)
  protected def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials)
  protected def loadDocumentSetViewers(id: Long): Iterable[DocumentSetUser]
}

object DocumentSetController extends DocumentSetController {
  override protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) = {
    OverviewDocumentSetCreationJob.findByUserWithDocumentSet(userEmail, pageSize, page)
  }
  protected override def loadDocumentSets(userEmail: String, pageSize: Int, page: Int) : ResultPage[OverviewDocumentSet] = {
    OverviewDocumentSet.findByUserId(userEmail, pageSize, page)
  }
  protected override def loadDocumentSet(id: Long): Option[DocumentSet] = DocumentSetFinder.byDocumentSet(id).headOption
  protected override def saveDocumentSet(documentSet: DocumentSet): DocumentSet = documentSet.save

  protected override def setDocumentSetUserRole(documentSet: DocumentSet, email: String, role: Ownership.Value) = OverviewDocumentSet(documentSet).setUserRole(email, role)
  protected override def removeDocumentSetViewer(documentSet: DocumentSet, email: String) = OverviewDocumentSet(documentSet).removeViewer(email)

  protected override def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials) =
    documentSet.createDocumentSetCreationJob(username = credentials.username, password = credentials.password)

  protected override def loadDocumentSetViewers(id: Long): Iterable[DocumentSetUser] = OverviewDocumentSet.findViewers(id)

}
