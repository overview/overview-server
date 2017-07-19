package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend

class ImportJobController @Inject() (
  importJobBackend: ImportJobBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {

  def index = authorizedAction(anyUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexByUser(request.user.email)
    } yield {
      Ok(views.json.ImportJob.index(jobs))
        .withHeaders(CACHE_CONTROL -> "max-age=0")
    }
  }
}
