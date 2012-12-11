package controllers

import java.sql.Connection
import play.api.mvc.{Request,AnyContent}

import models.OverviewUser

object HelpController extends BaseController with TransactionActionController {
  def show() = optionallyAuthorizedAction({user: Option[OverviewUser] => optionallyAuthorizedShow(user)(_: Request[AnyContent], _: Connection)})

  private def optionallyAuthorizedShow(optionalUser: Option[OverviewUser])(implicit request: Request[AnyContent], connection: Connection) = {
    Ok(views.html.Help.show(optionalUser))
  }
}
