package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import controllers.backend.{ApiTokenBackend,ViewBackend}
import controllers.forms.ViewForm
import models.orm.finders.{DocumentSetCreationJobFinder,TreeFinder}
import org.overviewproject.tree.orm.{DocumentSetCreationJob,Tree}
import org.overviewproject.models.{ApiToken,View}

trait ViewController extends Controller {
  def indexJson(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async {
    val trees = storage.findTrees(documentSetId).map(_.copy()).toArray
    val jobs = storage.findViewJobs(documentSetId).map(_.copy()).toArray

    viewBackend.index(documentSetId)
      .map((vs) => Ok(views.json.View.index(trees, vs, jobs)).withHeaders(CACHE_CONTROL -> "max-age=0"))
      .recover { case t: Throwable => InternalServerError(t.getMessage) }
  }

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val form = ViewForm.create(request.user.email)
    form.bindFromRequest.value match {
      case None => Future.successful(BadRequest("You must POST a 'title' and 'url'."))
      case Some(attributes) => {
        val result: Future[Result] = for {
          u <- appUrlChecker.check(attributes.url + "/metadata")
          apiToken <- apiTokenBackend.create(documentSetId, ApiToken.CreateAttributes(request.user.email, attributes.title))
          view <- viewBackend.create(documentSetId, View.CreateAttributes(attributes.url, apiToken.token, attributes.title))
        } yield Created(views.json.api.View.show(view))
        result.recover { case t: Throwable => BadRequest(t.getMessage) }
      }
    }
  }

  protected val storage: ViewController.Storage
  protected val apiTokenBackend: ApiTokenBackend
  protected val appUrlChecker: ViewController.AppUrlChecker
  protected val viewBackend: ViewBackend
}

object ViewController extends ViewController {
  trait AppUrlChecker {
    def check(url: String): Future[Unit]
  }

  object WsAppUrlChecker extends AppUrlChecker {
    override def check(url: String): Future[Unit] = {
      import play.api.Play.current
      import play.api.libs.concurrent.Execution.Implicits._

      val isAbsolute = url.startsWith("http")
      val absoluteUrl = if (isAbsolute) url else ("https:" + url)
      WS.url(absoluteUrl).get.map(x => ())
    }
  }

  trait Storage {
    def findTrees(documentSetId: Long) : Iterable[Tree]
    def findViewJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

  object DatabaseStorage extends Storage {
    override def findTrees(documentSetId: Long) = {
      TreeFinder.byDocumentSet(documentSetId).toSeq
    }

    override def findViewJobs(documentSetId: Long) = {
      DocumentSetCreationJobFinder
        .byDocumentSet(documentSetId)
        .excludeCancelledJobs
    }
  }

  override protected val storage = DatabaseStorage
  override protected val apiTokenBackend = ApiTokenBackend
  override protected val appUrlChecker = WsAppUrlChecker
  override protected val viewBackend = ViewBackend
}
