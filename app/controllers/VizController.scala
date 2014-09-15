package controllers

import play.api.mvc.{Controller,Result}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import controllers.backend.{ApiTokenBackend,VizBackend}
import controllers.forms.VizForm
import models.orm.finders.{DocumentSetCreationJobFinder,TreeFinder}
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.models.{ApiToken,Viz,VizLike}

trait VizController extends Controller {
  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async {
    val vizs = storage.findVizs(documentSetId)
    val jobs = storage.findVizJobs(documentSetId)

    vizBackend.index(documentSetId)
      .map((realVizs) => Ok(views.json.Viz.index(vizs ++ realVizs, jobs)).withHeaders(CACHE_CONTROL -> "max-age=0"))
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
    def findVizs(documentSetId: Long) : Iterable[VizLike]
    def findVizJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

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
  override protected val apiTokenBackend = ApiTokenBackend
  override protected val appUrlChecker = WsAppUrlChecker
  override protected val vizBackend = VizBackend
}
