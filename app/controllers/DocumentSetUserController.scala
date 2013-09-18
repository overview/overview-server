package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.Ownership
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.DocumentSetUserForm
import org.overviewproject.tree.orm.DocumentSetUser
import models.orm.finders.DocumentSetUserFinder
import models.orm.stores.DocumentSetUserStore

trait DocumentSetUserController extends Controller {
  import Authorities._

  trait Storage {
    def loadDocumentSetUsers(documentSetId: Long, role: Option[Ownership.Value]): Iterable[DocumentSetUser]
    def insertOrUpdateDocumentSetUser(documentSetUser: DocumentSetUser): Unit
    def deleteDocumentSetUser(documentSetId: Long, userEmail: String): Unit
  }

  /** @return a JSON list of Viewers.
    *
    * This method only lists DocumentSetUsers which have a role of Ownership.Viewer.
    *
    * Errors:
    * * Exception if there's an unknown storage error.
    */
  def indexJson(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val viewers = storage.loadDocumentSetUsers(documentSetId, Some(Ownership.Viewer))
    Ok(views.json.DocumentSetUser.index(viewers))
  }

  /** Creates a new entry in the DocumentSetUser table.
    *
    * Duplicate requests have no effect: the second insert will
    * be an update.
    *
    * Errors:
    * * BadRequest if the form (email and role) is invalid.
    * * BadRequest if the user is trying to edit him/herself.
    * * Exception if the DocumentSet disappears mid-request.
    * * Exception if there's an unknown storage error.
    */
  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    DocumentSetUserForm(documentSetId).bindFromRequest().fold(
      f => BadRequest,
      { dsu =>
        if (dsu.userEmail == request.user.email) {
          BadRequest
        } else {
          storage.insertOrUpdateDocumentSetUser(dsu)
          Ok
        }
      }
    )
  }

  /** Removes an entry from the DocumentSetUser table.
    *
    * Duplicate requests have no effect: the second deletion will
    * execute silently.
    *
    * Errors:
    * * BadRequest if the user is trying to delete him/herself.
    * * 500 if there's an unknown storage error.
    */
  def delete(documentSetId: Long, userEmail: String) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    if (userEmail == request.user.email) {
      BadRequest
    } else {
      storage.deleteDocumentSetUser(documentSetId, userEmail)
      Ok
    }
  }

  protected val storage: DocumentSetUserController.Storage
}

object DocumentSetUserController extends DocumentSetUserController {
  object DatabaseStorage extends Storage {
    override def loadDocumentSetUsers(documentSetId: Long, roleOption: Option[Ownership.Value]) = {
      roleOption match {
        case Some(role) => DocumentSetUserFinder.byDocumentSetAndRole(documentSetId, role)
        case None => DocumentSetUserFinder.byDocumentSet(documentSetId)
      }
    }

    override def insertOrUpdateDocumentSetUser(documentSetUser: DocumentSetUser) = {
      DocumentSetUserStore.insertOrUpdate(documentSetUser)
    }

    override def deleteDocumentSetUser(documentSetId: Long, userEmail: String) = {
      val query = DocumentSetUserFinder.byDocumentSetAndUser(documentSetId, userEmail)
      DocumentSetUserStore.delete(query)
    }
  }
  override val storage = DatabaseStorage
}
