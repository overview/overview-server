package controllers

import play.api.mvc.Controller

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser

object WelcomeController extends Controller {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()

  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user.map(user =>
      Redirect(routes.DocumentSetController.index())
    ).getOrElse(
      Ok(views.html.Welcome.show(loginForm, userForm, is32BitJava))
    )
  }

  lazy val is32BitJava = sys.props.get("overview.is32BitJava").isDefined
}
