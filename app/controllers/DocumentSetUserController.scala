package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.data.validation.{Constraints,Invalid,Valid}
import scala.concurrent.Future

import controllers.auth.AuthConfig
import controllers.auth.Authorities.{userOwningDocumentSet,userCanExportDocumentSet}
import controllers.backend.{DocumentSetBackend,DocumentSetUserBackend}

class DocumentSetUserController @Inject() (
  val backend: DocumentSetUserBackend,
  val documentSetBackend: DocumentSetBackend,
  val controllerComponents: ControllerComponents,
  authConfig: AuthConfig,
  val indexHtml: views.html.DocumentSetUser.index
) extends BaseController {
  def index(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      emails <- backend.index(documentSetId).map(_.map(_.userEmail))
      isPublic <- documentSetBackend.show(documentSetId).map(_.get.public)
    } yield Ok(indexHtml(request.user, documentSetId, emails, isPublic))
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
  def update(documentSetId: Long, userEmail: String) = authorizedAction(userCanExportDocumentSet(authConfig, documentSetId)).async { implicit request =>
    Constraints.emailAddress()(userEmail) match {
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
  def delete(documentSetId: Long, userEmail: String) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    backend.destroy(documentSetId, userEmail)
      .map(_ => NoContent)
  }
}
