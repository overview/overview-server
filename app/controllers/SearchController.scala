package controllers

import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.SearchForm
import org.overviewproject.jobs.models.Search
import controllers.util.JobQueueSender
import play.api.Logger
import play.api.libs.json.Json
import scala.concurrent.Future

trait SearchController extends Controller {
  val jobQueue: SearchController.JobQueue
  val form = SearchForm

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    form(documentSetId).bindFromRequest.fold(
      formWithErrors => Future(BadRequest),
      search => jobQueue.createSearch(search).map((Unit) => Accepted(Json.obj()))
    )
  }
}

object SearchController extends SearchController {
  trait JobQueue {
    def createSearch(search: Search): Future[Unit]
  }

  override val jobQueue = new JobQueue {
    override def createSearch(search: Search) = JobQueueSender.send(search)
  }
}
