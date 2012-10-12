package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.LoginLogout
import play.api.mvc.{AnyContent,Controller,Request}

import models.OverviewUser

object SessionController extends BaseController with LoginLogout {
  val loginForm = controllers.forms.LoginForm()
  val registrationForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.SessionController")

  def new_() = optionallyAuthorizedAction({ user: Option[User] => optionallyAuthorizedNew_(user)(_: Request[AnyContent], _: Connection)})

  def optionallyAuthorizedNew_(optionalUser: Option[User])(implicit request: Request[AnyContent], connection: Connection) = {
    optionalUser match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case _ => Ok(views.html.Session.new_(loginForm, registrationForm))
    }
  }

  def delete = ActionInTransaction { (request: Request[AnyContent], connection: Connection) =>
    implicit val r = request
    gotoLogoutSucceeded.flashing("success" -> m("delete.success"))
  }

  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) =>
    implicit val r = request
    implicit val c = connection

    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.Session.new_(formWithErrors, registrationForm)),
      user => {
        user.recordLogin(request.remoteAddress, new java.util.Date()).save
        gotoLoginSucceeded(user.id).flashing("success" -> m("create.success"))
      }
    )
  }
}
