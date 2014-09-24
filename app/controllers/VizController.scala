package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import controllers.backend.{ApiTokenBackend,VizBackend}
import controllers.forms.VizForm
import models.orm.finders.{DocumentSetCreationJobFinder,TreeFinder}
import org.overviewproject.tree.orm.{DocumentSetCreationJob,Tree}
import org.overviewproject.models.{ApiToken,Viz}

trait VizController extends Controller {
  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async {
    val trees = storage.findTrees(documentSetId).map(_.copy()).toArray
    val jobs = storage.findVizJobs(documentSetId).map(_.copy()).toArray

    vizBackend.index(documentSetId)
      .map((vizs) => Ok(views.json.Viz.index(trees, vizs, jobs)).withHeaders(CACHE_CONTROL -> "max-age=0"))
      .recover { case t: Throwable => InternalServerError(t.getMessage) }
  }

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val form = VizForm.create(request.user.email)
    form.bindFromRequest.value match {
      case None => Future.successful(BadRequest("You must POST a 'title' and 'url'."))
      case Some(attributes) => {
        val result: Future[Result] = for {
          u <- appUrlChecker.check(attributes.url + "/metadata")
          apiToken <- apiTokenBackend.create(documentSetId, ApiToken.CreateAttributes(request.user.email, attributes.title))
          viz <- vizBackend.create(documentSetId, Viz.CreateAttributes(attributes.url, apiToken.token, attributes.title, Json.obj()))
        } yield Created(views.json.api.Viz.show(viz))
        result.recover { case t: Throwable => BadRequest(t.getMessage) }
      }
    }
  }

  protected val storage: VizController.Storage
  protected val apiTokenBackend: ApiTokenBackend
  protected val appUrlChecker: VizController.AppUrlChecker
  protected val vizBackend: VizBackend
}

object VizController extends VizController {
  trait AppUrlChecker {
    def check(url: String): Future[Unit]
  }

  object WsAppUrlChecker extends AppUrlChecker {
    override def check(url: String): Future[Unit] = {
      import play.api.Play.current
      import play.api.libs.concurrent.Execution.Implicits._
      WS.url(url).get.map(x => ())
    }
  }

  trait Storage {
    def findTrees(documentSetId: Long) : Iterable[Tree]
    def findVizJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

  object DatabaseStorage extends Storage {
    override def findTrees(documentSetId: Long) = {
      TreeFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findVizJobs(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .excludeCancelledJobs
    }
  }

  override protected val storage = DatabaseStorage
  override protected val apiTokenBackend = ApiTokenBackend
  override protected val appUrlChecker = WsAppUrlChecker
  override protected val vizBackend = VizBackend
}
