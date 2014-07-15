package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob }
import controllers.auth.{AuthorizedAction,Authorities}
import models.orm.finders.DocumentSetCreationJobFinder

trait ImportJobController extends Controller {
  import Authorities._

  def index() = AuthorizedAction.inTransaction(anyUser) { implicit request =>
    val tuples = loadDocumentSetCreationJobs(request.user.email)

    Ok(views.json.ImportJob.index(tuples))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected def loadDocumentSetCreationJobs(userEmail: String) : Seq[(DocumentSetCreationJob, DocumentSet, Long)]
}

object ImportJobController extends ImportJobController {
  override protected def loadDocumentSetCreationJobs(userEmail: String) = {
    DocumentSetCreationJobFinder
      .byUser(userEmail)
      .excludeTreeCreationJobs
      .excludeCancelledJobs
      .withDocumentSetsAndQueuePositions
      .toSeq
  }
}
