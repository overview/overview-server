package controllers

import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.MessagesApi

import controllers.auth.OptionallyAuthorizedAction
import controllers.auth.Authorities.anyUser
import play.api.Play

class WelcomeController @Inject() (
  configuration: Configuration,
  val controllerComponents: ControllerComponents,
  showHtml: views.html.Welcome.show
) extends BaseController {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()
  val banner = configuration.get[String]("overview.welcome_banner")
  val allowRegistration = configuration.get[Boolean]("overview.allow_registration")
  lazy val is32BitJava = sys.props.get("overview.is32BitJava").isDefined

  def show() = optionallyAuthorizedAction(anyUser) { implicit request =>
    request.user.map(user =>
      Redirect(routes.DocumentSetController.index())
    ).getOrElse(
      Ok(showHtml(loginForm, userForm, is32BitJava, banner, allowRegistration))
    )
  }

}
