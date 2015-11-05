package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.libs.ws.WS
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet,userOwningView}
import controllers.backend.{ApiTokenBackend,StoreBackend,ViewBackend}
import controllers.forms.{ViewForm,ViewUpdateAttributesForm}
import com.overviewdocs.models.{ApiToken,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType,Tree,View}
import com.overviewdocs.models.tables.{DocumentSetCreationJobs,Trees}

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
          apiToken <- apiTokenBackend.create(Some(documentSetId), ApiToken.CreateAttributes(request.user.email, attributes.title))
          view <- viewBackend.create(documentSetId, View.CreateAttributes(attributes.url, apiToken.token, attributes.title))
        } yield Created(views.json.api.View.show(view))
        result.recover { case t: Throwable => BadRequest(t.getMessage) }
      }
    }
  }

  def update(documentSetId: Long, viewId: Long) = AuthorizedAction(userOwningView(viewId)).async { implicit request =>
    ViewUpdateAttributesForm().bindFromRequest.fold(
      f => Future.successful(BadRequest),
      attributes => viewBackend.update(viewId, attributes).map(_ match {
        case Some(view) => Ok(views.json.View.show(view))
        case None => NotFound
      })
    )
  }

  def destroy(documentSetId: Long, viewId: Long) = AuthorizedAction(userOwningView(viewId)).async { request =>
    viewBackend.show(viewId).flatMap(_ match {
      case Some(view) => {
        for {
          unit1 <- storeBackend.destroy(view.apiToken)
          unit2 <- viewBackend.destroy(view.id)
          unit3 <- apiTokenBackend.destroy(view.apiToken)
        } yield NoContent
      }
      case None => Future.successful(NotFound) // this is unlikely -- userOwningView() would normally fail
    })
  }

  protected val storage: ViewController.Storage

  protected val apiTokenBackend: ApiTokenBackend
  protected val storeBackend: StoreBackend
  protected val viewBackend: ViewBackend
}

object ViewController extends ViewController {
  trait Storage {
    def findTrees(documentSetId: Long) : Iterable[Tree]
    def findViewJobs(documentSetId: Long) : Iterable[DocumentSetCreationJob]
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def findTrees(documentSetId: Long) = {
      blockingDatabase.seq(Trees.filter(_.documentSetId === documentSetId))
    }

    override def findViewJobs(documentSetId: Long) = {
      blockingDatabase.seq(
        DocumentSetCreationJobs
          .filter(_.documentSetId === documentSetId)
          .filter(_.state =!= DocumentSetCreationJobState.Cancelled)
          .filter(_.jobType === DocumentSetCreationJobType.Recluster)
      )
    }
  }

  override protected val storage = DatabaseStorage

  override protected val apiTokenBackend = ApiTokenBackend
  override protected val storeBackend = StoreBackend
  override protected val viewBackend = ViewBackend
}
