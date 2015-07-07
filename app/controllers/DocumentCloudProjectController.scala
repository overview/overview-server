package controllers

import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend

trait DocumentCloudProjectController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(views.html.DocumentCloudProject.index(request.user, count))
  }
}

object DocumentCloudProjectController extends DocumentCloudProjectController {
  override val documentSetBackend = DocumentSetBackend
}
