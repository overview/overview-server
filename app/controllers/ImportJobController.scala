package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob }
import controllers.auth.{AuthorizedAction,Authorities}
import models.ResultPage
import models.orm.finders.DocumentSetCreationJobFinder

trait ImportJobController extends Controller {
  import Authorities._

  private val pageSize = 50 // we'll only show page 1, so pageSize is a guard against DoS

  def index() = AuthorizedAction(anyUser) { implicit request =>
    val tuples = loadDocumentSetCreationJobs(request.user.email, pageSize, 1)

    Ok(views.json.ImportJob.index(tuples))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int)
    : ResultPage[(DocumentSetCreationJob, DocumentSet, Long)]
}

object ImportJobController extends ImportJobController {
  override protected def loadDocumentSetCreationJobs(userEmail: String, pageSize: Int, page: Int) = {
    val query = DocumentSetCreationJobFinder.byUser(userEmail).withDocumentSetsAndQueuePositions
    ResultPage(query, pageSize, page)
  }
}
