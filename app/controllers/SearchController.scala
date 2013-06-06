package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.SearchForm
import org.overviewproject.jobs.models.Search
import controllers.util.JobQueueSender

trait SearchController extends Controller {
  trait JobQueue {
    def createSearch(search: Search): Unit
  }

  val jobQueue : SearchController.JobQueue
  val form = SearchForm

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    form(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      search => {
        jobQueue.createSearch(search)
        Accepted
      }
    )
  }
}

object SearchController extends SearchController {
  override val jobQueue = new JobQueue {
    def createSearch(search: Search) = {
      JobQueueSender.send(search)
    }
  }
}
