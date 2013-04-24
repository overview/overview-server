package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.orm.DocumentSetCreationJob
import controllers.auth.{AuthorizedAction,Authorities}
import models.ResultPage
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.DocumentSet

trait DocumentSetCreationJobController extends Controller {
  import Authorities._

  private val pageSize = 50 // we'll only show page 1, so pageSize is a guard against DoS

  def index() = AuthorizedAction(anyUser) { implicit request =>
    val tuples = loadDocumentSetCreationJobs(request.user.email, pageSize, 1)

    Ok(views.json.DocumentSetCreationJob.index(tuples))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int)
    : ResultPage[(DocumentSetCreationJob, DocumentSet, Long)]
}

object DocumentSetCreationJobController extends DocumentSetCreationJobController {
  override protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) = {
    val query = DocumentSetCreationJobFinder.byUser(userEmail).withDocumentSetsAndQueuePositions
    ResultPage(query, pageSize, page)
  }
}
