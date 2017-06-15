package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.MessagesApi

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser
import play.api.Play

class WelcomeController @Inject() (
  configuration: Configuration,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()
  val banner = configuration.getString("overview.welcome_banner")
  val allowRegistration = configuration.getBoolean("overview.allow_registration").getOrElse(false)
  lazy val is32BitJava = sys.props.get("overview.is32BitJava").isDefined

  def show() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user.map(user =>
      Redirect(routes.DocumentSetController.index())
    ).getOrElse(
      Ok(views.html.Welcome.show(loginForm, userForm, is32BitJava, banner, allowRegistration))
    )
  }

}
