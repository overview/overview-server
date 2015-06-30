package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import scala.concurrent.Future

import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.auth.Authorities.anyUser
import controllers.backend.SessionBackend

trait SessionController extends Controller {
  private val loginForm = controllers.forms.LoginForm()
  private val registrationForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.SessionController")

  protected val sessionBackend: SessionBackend

  def _new() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case _ => Ok(views.html.Session._new(loginForm, registrationForm))
    }
  }

  def delete = OptionallyAuthorizedAction(anyUser).async { implicit request =>
    val result = AuthResults.logoutSucceeded(request).flashing(
      "success" -> m("delete.success"),
      "event" -> "session-delete"
    )

    request.userSession match {
      case Some(session) => {
        for {
          _ <- sessionBackend.destroy(session.id)
        } yield result
      }
      case None => Future.successful(result)
    }
  }

  def create = Action.async { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest(views.html.Session._new(formWithErrors, registrationForm))),
      user => {
        for {
          _ <- sessionBackend.destroyExpiredSessionsForUserId(user.id)
          session <- sessionBackend.create(user.id, request.remoteAddress)
        } yield AuthResults.loginSucceeded(request, session).flashing("event" -> "session-create")
      }
    )
  }
}

object SessionController extends SessionController {
  override protected val sessionBackend = SessionBackend
}
