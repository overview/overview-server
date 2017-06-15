package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend

class ImportJobController @Inject() (
  importJobBackend: ImportJobBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {

  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexByUser(request.user.email)
    } yield {
      Ok(views.json.ImportJob.index(jobs))
        .withHeaders(CACHE_CONTROL -> "max-age=0")
    }
  }
}
