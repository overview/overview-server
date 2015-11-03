package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend

trait ImportJobController extends Controller {
  val importJobBackend: ImportJobBackend

  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexByUser(request.user.email)
    } yield {
      Ok(views.json.ImportJob.index(jobs.toSeq))
        .withHeaders(CACHE_CONTROL -> "max-age=0")
    }
  }
}

object ImportJobController extends ImportJobController {
  override val importJobBackend = ImportJobBackend
}
