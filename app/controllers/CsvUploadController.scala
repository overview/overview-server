package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.backend.{CsvImportBackend,DocumentSetBackend}

class CsvUploadController @Inject() (
  csvImportBackend: CsvImportBackend,
  documentSetBackend: DocumentSetBackend,
  val controllerComponents: ControllerComponents,
  csvUploadNewHtml: views.html.CsvUpload._new
) extends BaseController {
  def _new() = authorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(csvUploadNewHtml(request.user, count))
  }

  def delete(documentSetId: Long, csvImportId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      _ <- csvImportBackend.cancel(documentSetId, csvImportId)
    } yield Redirect(routes.DocumentSetController.show(documentSetId))
  }
}
