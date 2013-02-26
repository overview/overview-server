package controllers

import java.sql.Connection
import play.api.mvc.Controller
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetCreationJobType.DocumentCloudJob
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import controllers.forms.DocumentSetForm.Credentials
import models.orm.{ DocumentSet, User }
import models.orm.DocumentSet.ImplicitHelper._
import models.orm.DocumentSetUserRoleType._
import models.{ OverviewDocumentSet, ResultPage }
import models.orm.DocumentSetUser
import models.orm.DocumentSetUserRoleType
import controllers.forms.UserRoleForm

trait DocumentSetController extends Controller {
  import Authorities._

  private val form = DocumentSetForm()
  private val pageSize = 10

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSets = OverviewDocumentSet.findByUserId(request.user.email, pageSize, realPage)

    val publicDocumentSets = OverviewDocumentSet.findPublic

    Ok(views.html.DocumentSet.index(request.user, documentSets, form, publicDocumentSets))
  }

  def show(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val documentSet = OverviewDocumentSet.findById(id)
    documentSet match {
      case Some(ds) => Ok(views.html.DocumentSet.show(request.user, ds))
      case None => NotFound
    }
  }

  def showJson(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    OverviewDocumentSet.findById(id) match {
      case Some(ds) => Ok(views.json.DocumentSet.show(ds))
      case None => NotFound
    }
  }

  def create() = AuthorizedAction(anyUser) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    form.bindFromRequest().fold(
      f => index(1)(request),
      (tuple) => {
        val documentSet = tuple._1
        val credentials = tuple._2

        val saved = saveDocumentSet(documentSet)
        setDocumentSetUserRole(saved, request.user.email, Owner)
        createDocumentSetCreationJob(saved, credentials)

        Redirect(routes.DocumentSetController.index()).flashing("success" -> m("create.success"))
      })
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    OverviewDocumentSet.delete(id)
    Redirect(routes.DocumentSetController.index()).flashing("success" -> m("delete.success"))
  }

  def update(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val documentSet = loadDocumentSet(id)
    documentSet.map { d =>
      DocumentSetUpdateForm(d).bindFromRequest().fold(
        f => BadRequest, { updatedDocumentSet =>
          saveDocumentSet(updatedDocumentSet)
          Ok
        })
    }.getOrElse(NotFound)
  }

  import play.api.data.Form
  import play.api.data.Forms._

  val createCloneForm = Form(
    single("sourceDocumentSetId" -> number))

  def createClone = AuthorizedAction(anyUser) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")
    createCloneForm.bindFromRequest().fold(
      f => BadRequest, { id =>
        val cloneStatus = loadDocumentSet(id).map { d =>
          OverviewDocumentSet(d).cloneForUser(request.user.id)
          ("success" -> m("create.success"))
        }.getOrElse("error" -> m("clone.failure"))
        Redirect(routes.DocumentSetController.index()).flashing(cloneStatus)
      })
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

  def removeUser(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    loadDocumentSet(id).map { ds =>
      UserRoleForm(id).bindFromRequest().fold(
        f => BadRequest, { dsu =>
          removeDocumentSetUserRoled(ds, dsu.userEmail, dsu.role)
          Ok
        })
    }.getOrElse(NotFound)
  }

  protected def loadDocumentSet(id: Long): Option[DocumentSet]
  protected def saveDocumentSet(documentSet: DocumentSet): DocumentSet
  protected def setDocumentSetUserRole(documentSet: DocumentSet, email: String, role: DocumentSetUserRoleType)
  protected def removeDocumentSetUserRoled(documentSet: DocumentSet, email: String, role: DocumentSetUserRoleType)
  protected def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials)
  protected def loadDocumentSetViewers(id: Long): Iterable[DocumentSetUser]
}

object DocumentSetController extends DocumentSetController {
  protected def loadDocumentSet(id: Long): Option[DocumentSet] = DocumentSet.findById(id)
  protected def saveDocumentSet(documentSet: DocumentSet): DocumentSet = documentSet.save
  // TODO: handle roles other than Viewer
  protected def setDocumentSetUserRole(documentSet: DocumentSet, email: String, role: DocumentSetUserRoleType) = OverviewDocumentSet(documentSet).addViewer(email)
  protected def removeDocumentSetUserRoled(documentSet: DocumentSet, email: String, role: DocumentSetUserRoleType) = OverviewDocumentSet(documentSet).removeViewer(email)

  protected def createDocumentSetCreationJob(documentSet: DocumentSet, credentials: Credentials) =
    documentSet.createDocumentSetCreationJob(username = credentials.username, password = credentials.password)

  protected def loadDocumentSetViewers(id: Long): Iterable[DocumentSetUser] = OverviewDocumentSet.findViewers(id)

}
