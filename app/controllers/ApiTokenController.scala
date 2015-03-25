package controllers

import play.api.data.{Form,Forms}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.ApiTokenBackend
import org.overviewproject.models.ApiToken

trait ApiTokenController extends Controller {
  protected val backend: ApiTokenBackend

  def index(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    render.async {
      case Accepts.Html() => {
        Future.successful(Ok(views.html.ApiToken.index(request.user, id)))
      }
      case Accepts.Json() => {
        backend.index(request.user.email, Some(id))
          .map(tokens => Ok(views.json.ApiToken.index(tokens)))
      }
    }
  }

  def create(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { implicit request =>
    val description = flatRequestData(request).getOrElse("description", "")
    val attributes = ApiToken.CreateAttributes(request.user.email, description)
    backend.create(Some(id), attributes)
      .map(token => Ok(views.json.ApiToken.show(token)))
  }

  /** Destroys the token.
    *
    * We don't need auth: if somebody _has_ the token, then that person is
    * authenticated by definition. Skipping auth here can only benefit the
    * legitimate owner of a token, by deleting his/her leaked token.
    */
  def destroy(id: Long, token: String) = Action.async { request =>
    backend.destroy(token).map(_ => NoContent)
  }
}

object ApiTokenController extends ApiTokenController {
  override protected val backend = ApiTokenBackend
}
