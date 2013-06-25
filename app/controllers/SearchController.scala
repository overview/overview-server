package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.forms.SearchForm
import org.overviewproject.jobs.models.Search
import controllers.util.JobQueueSender
import play.api.Logger

trait SearchController extends Controller {
  trait JobQueue {
    def createSearch(search: Search): Either[Unit, Unit]
  }

  val jobQueue: SearchController.JobQueue
  val form = SearchForm

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    form(documentSetId).bindFromRequest.fold(
      formWithErrors => BadRequest,
      search => {
        jobQueue.createSearch(search).fold(
          _ => {
            Logger.error("Not connected to Message Broker")
            InternalServerError
          },
          _ => Accepted)
      })
  }
}

object SearchController extends SearchController {
  override val jobQueue = new JobQueue {
    def createSearch(search: Search): Either[Unit, Unit] = {
      JobQueueSender.send(search)
    }
  }
}
