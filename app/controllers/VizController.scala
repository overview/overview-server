package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import models.orm.finders.{DocumentSetCreationJobFinder,TreeFinder}
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.models.Viz

trait VizController extends Controller {
  trait Storage {
    def findVizs(documentSetId: Long) : Iterable[Viz]
    def findVizJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

  def indexJson(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val vizs = storage.findVizs(documentSetId)
    val jobs = storage.findVizJobs(documentSetId)

    Ok(views.json.Viz.index(vizs, jobs))
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
