package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.data.validation.{Constraints,Invalid,Valid}
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userCanExportDocumentSet}
import controllers.backend.{DocumentSetBackend,DocumentSetUserBackend}

class DocumentSetUserController @Inject() (
  val backend: DocumentSetUserBackend,
  val documentSetBackend: DocumentSetBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      emails <- backend.index(documentSetId).map(_.map(_.userEmail))
      isPublic <- documentSetBackend.show(documentSetId).map(_.get.public)
    } yield Ok(views.html.DocumentSetUser.index(request.user, documentSetId, emails, isPublic))
  }

  /** Creates a new entry in the DocumentSetUser table.
    *
    * Duplicate requests have no effect: the second insert will
    * be an update.
    *
    * Errors:
    * * BadRequest if the form (email and role) is invalid.
    * * NotFound if the user tries to update an owner.
    */
  def update(documentSetId: Long, userEmail: String) = AuthorizedAction(userCanExportDocumentSet(documentSetId)).async { implicit request =>
    Constraints.emailAddress(userEmail) match {
      case Invalid(err) => Future.successful(BadRequest(err.head.message))
      case Valid => {
        backend.update(documentSetId, userEmail)
          .map(_ match {
            case Some(_) => NoContent
            case None => NotFound
          })
      }
    }
  }

  /** Removes an entry from the DocumentSetUser table.
    *
    * Duplicate requests have no effect: the second deletion will
    * be a no-op. Also, deleting the owner will have no effect.
    */
  def delete(documentSetId: Long, userEmail: String) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    backend.destroy(documentSetId, userEmail)
      .map(_ => NoContent)
  }
}
