package controllers

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

trait CsvUploadController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  def _new() = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByUserEmail(request.user.email)
    } yield Ok(views.html.CsvUpload._new(request.user, count))
  }
}

object CsvUploadController extends CsvUploadController {
  override val documentSetBackend = DocumentSetBackend
}
