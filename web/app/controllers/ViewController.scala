package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.mvc.Result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet,userOwningView}
import controllers.backend.{ApiTokenBackend,StoreBackend,ViewBackend}
import controllers.forms.{ViewForm,ViewUpdateAttributesForm}
import com.overviewdocs.models.{ApiToken,Tree,View}
import com.overviewdocs.models.tables.{Trees}

class ViewController @Inject() (
  storage: ViewController.Storage,
  apiTokenBackend: ApiTokenBackend,
  storeBackend: StoreBackend,
  viewBackend: ViewBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def indexJson(documentSetId: Long) = authorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    val trees = storage.findTrees(documentSetId).map(_.copy()).toArray

    viewBackend.index(documentSetId)
      .map((vs) => Ok(views.json.View.index(trees, vs)).withHeaders(CACHE_CONTROL -> "max-age=0"))
      .recover { case t: Throwable => InternalServerError(t.getMessage) }
  }

  def create(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val form = ViewForm.create(request.user.email)
    form.bindFromRequest.value match {
      case None => Future.successful(BadRequest("You must POST a 'title' and 'url'."))
      case Some(attributes) => {
        val result: Future[Result] = for {
          apiToken <- apiTokenBackend.create(
            Some(documentSetId),
            ApiToken.CreateAttributes(request.user.email, attributes.title)
          )
          view <- viewBackend.create(documentSetId, View.CreateAttributes(
            attributes.url,
            attributes.serverUrlFromPlugin,
            apiToken.token,
            attributes.title
          ))
        } yield Created(views.json.api.View.show(view))
        result.recover { case t: Throwable => BadRequest(t.getMessage) }
      }
    }
  }

  def update(documentSetId: Long, viewId: Long) = authorizedAction(userOwningView(viewId)).async { implicit request =>
    ViewUpdateAttributesForm().bindFromRequest.fold(
      f => Future.successful(BadRequest),
      attributes => viewBackend.update(viewId, attributes).map(_ match {
        case Some(view) => Ok(views.json.View.show(view))
        case None => NotFound
      })
    )
  }

  def destroy(documentSetId: Long, viewId: Long) = authorizedAction(userOwningView(viewId)).async { request =>
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
}

object ViewController {
  @ImplementedBy(classOf[ViewController.BlockingDatabaseStorage])
  trait Storage {
    def findTrees(documentSetId: Long) : Iterable[Tree]
  }

  class BlockingDatabaseStorage @Inject() extends Storage with HasBlockingDatabase {
    import database.api._

    override def findTrees(documentSetId: Long) = {
      blockingDatabase.seq(Trees.filter(_.documentSetId === documentSetId))
    }
  }
}
