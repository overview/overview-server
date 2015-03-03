package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import controllers.backend.ImportJobBackend
import org.overviewproject.models.{ DocumentSet, DocumentSetCreationJob }

trait ImportJobController extends Controller {
  val importJobBackend: ImportJobBackend

  def index() = AuthorizedAction(anyUser).async { implicit request =>
    for {
      allSortedIds <- importJobBackend.indexIdsInProcessingOrder
      jobsAndDocumentSets <- importJobBackend.indexWithDocumentSets(request.user.email)
    } yield {
      val tuples = jobsAndDocumentSets.map({ (job: DocumentSetCreationJob, documentSet: DocumentSet) =>
        (job, documentSet, allSortedIds.indexOf(job.id))
      }.tupled)
      Ok(views.json.ImportJob.index(tuples))
        .withHeaders(CACHE_CONTROL -> "max-age=0")
    }
  }
}

object ImportJobController extends ImportJobController {
  override val importJobBackend = ImportJobBackend
}
