package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend
import controllers.Controller

trait ImportJobController extends Controller {
  protected val importJobBackend: ImportJobBackend

  def index() = AuthorizedAction(adminUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexWithDocumentSetsAndOwners
    } yield Ok(views.html.admin.ImportJob.index(request.user, jobs.toIterable))
  }
}

object ImportJobController extends ImportJobController {
  override protected val importJobBackend = ImportJobBackend
}
