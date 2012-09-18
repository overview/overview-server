package controllers

import java.sql.Connection
import play.api.mvc.{Request,AnyContent}

object WelcomeController extends BaseController with TransactionActionController {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()

  def show() = optionallyAuthorizedAction({user: Option[User] => optionallyAuthorizedShow(user)(_: Request[AnyContent], _: Connection)})

  private def optionallyAuthorizedShow(optionalUser: Option[User])(implicit request: Request[AnyContent], connection: Connection) = {
    optionalUser.map(user =>
      Redirect(routes.DocumentSetController.index())
    ).getOrElse(
      Ok(views.html.Welcome.show(loginForm, userForm))
    )
  }
}
