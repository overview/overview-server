package controllers

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser
import play.api.Play

object WelcomeController extends Controller {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()
  val banner = Play.current.configuration.getString("overview.welcome_banner")
  val allowRegistration = Play.current.configuration.getBoolean("overview.allow_registration").getOrElse(false)
  lazy val is32BitJava = sys.props.get("overview.is32BitJava").isDefined

  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user.map(user =>
      Redirect(routes.DocumentSetController.index())
    ).getOrElse(
      Ok(views.html.Welcome.show(loginForm, userForm, is32BitJava, banner, allowRegistration))
    )
  }

}
