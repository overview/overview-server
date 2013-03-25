package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.orm.UploadedFile
import models.ResultPage
import models.{OverviewDocumentSet, OverviewDocumentSetCreationJob}
import controllers.auth.{AuthorizedAction,Authorities}
import models.orm.finders.DocumentSetCreationJobFinder

trait DocumentSetCreationJobController extends Controller {
  import Authorities._

  private val pageSize = 50 // we'll only show page 1, so pageSize is a guard against DoS

  def index() = AuthorizedAction(anyUser) { implicit request =>
    val tuples = loadDocumentSetCreationJobs(request.user.email, pageSize, 1)

    Ok(views.json.DocumentSetCreationJob.index(tuples))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int)
    : ResultPage[(OverviewDocumentSetCreationJob, OverviewDocumentSet)]
}

object DocumentSetCreationJobController extends DocumentSetCreationJobController {
  override protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) = {
    OverviewDocumentSetCreationJob.findByUserWithDocumentSet(userEmail, pageSize, page)
  }
}
