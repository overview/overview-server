package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import models.orm.finders.{DocumentSetCreationJobFinder,TreeFinder}
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.models.VizLike

trait VizController extends Controller {
  trait Storage {
    def findVizs(documentSetId: Long) : Iterable[VizLike]
    def findVizJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val vizs = storage.findVizs(documentSetId)
    val jobs = storage.findVizJobs(documentSetId)

    Ok(views.json.Viz.index(vizs, jobs))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val storage: VizController.Storage
}

object VizController extends VizController {
  object DatabaseStorage extends Storage {
    override def findVizs(documentSetId: Long) = {
      TreeFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findVizJobs(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .excludeCancelledJobs
    }
  }

  override protected val storage = DatabaseStorage
}
