package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.backend.{CsvImportBackend,DocumentSetBackend}

class CsvUploadController @Inject() (
  csvImportBackend: CsvImportBackend,
  documentSetBackend: DocumentSetBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def _new() = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(views.html.CsvUpload._new(request.user, count))
  }

  def delete(documentSetId: Long, csvImportId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      _ <- csvImportBackend.cancel(documentSetId, csvImportId)
    } yield Redirect(routes.DocumentSetController.show(documentSetId))
  }
}
