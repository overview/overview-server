package controllers

import java.sql.Connection
import play.api.mvc.{Request,AnyContent}

object WelcomeController extends BaseController with TransactionActionController {
  def show() = optionallyAuthorizedAction({user: Option[User] => optionallyAuthorizedShow(user)(_: Request[AnyContent], _: Connection)})

  private def optionallyAuthorizedShow(user: Option[User])(implicit request: Request[AnyContent], connection: Connection) = {
    Ok(views.html.Welcome.show(user))
  }
}
