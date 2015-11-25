package controllers

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.backend.{CsvImportBackend,DocumentSetBackend}

trait CsvUploadController extends Controller {
  protected val csvImportBackend: CsvImportBackend
  protected val documentSetBackend: DocumentSetBackend

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

object CsvUploadController extends CsvUploadController {
  override protected val csvImportBackend = CsvImportBackend
  override protected val documentSetBackend = DocumentSetBackend
}
