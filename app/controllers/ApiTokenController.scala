package controllers

import play.api.data.{Form,Forms}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action,Result}
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.backend.ApiTokenBackend
import com.overviewdocs.models.ApiToken

trait ApiTokenController extends Controller {
  protected val backend: ApiTokenBackend

  private def indexHtml(documentSetId: Option[Long])(implicit request: AuthorizedRequest[_]): Future[Result] = {
    Future.successful(Ok(views.html.ApiToken.index(request.user, documentSetId)))
  }

  private def indexJson(documentSetId: Option[Long])(implicit request: AuthorizedRequest[_]): Future[Result] = {
    backend.index(request.user.email, documentSetId)
      .map(tokens => Ok(views.json.ApiToken.index(tokens)))
  }

  private def indexAny(documentSetId: Option[Long])(implicit request: AuthorizedRequest[_]): Future[Result] = {
    render.async {
      case Accepts.Html() => indexHtml(documentSetId)
      case Accepts.Json() => indexJson(documentSetId)
    }
  }

  def indexForDocumentSet(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request
    => indexAny(Some(id))
  }
  def index = AuthorizedAction(anyUser).async { implicit request => indexAny(None) }

  private def createAny(documentSetId: Option[Long])(implicit request: AuthorizedRequest[_]): Future[Result] = {
    val description = flatRequestData(request).getOrElse("description", "")
    val attributes = ApiToken.CreateAttributes(request.user.email, description)
    backend.create(documentSetId, attributes)
      .map(token => Ok(views.json.ApiToken.show(token)))
  }

  def createForDocumentSet(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    createAny(Some(id))
  }
  def create = AuthorizedAction(anyUser).async { implicit request => createAny(None) }

  private def realDestroy(token: String): Future[Result] = backend.destroy(token).map(_ => NoContent)

  /** Destroys the token.
    *
    * We don't need auth: if somebody _has_ the token, then that person is
    * authenticated by definition. Skipping auth here can only benefit the
    * legitimate owner of a token, by deleting his/her leaked token.
    */
  def destroyForDocumentSet(id: Long, token: String) = Action.async { _ => realDestroy(token) }
  def destroy(token: String) = Action.async { _ => realDestroy(token) }
}

object ApiTokenController extends ApiTokenController {
  override protected val backend = ApiTokenBackend
}
