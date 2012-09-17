package controllers

import java.sql.Connection
import models.{OverviewUser, PotentialUser}
import play.api.mvc.{Action,AnyContent,Controller,Request}
import play.api.data.Form
import play.api.data.Forms._
import jp.t2v.lab.play20.auth.LoginLogout

import models.orm.User

object SessionController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl with HttpsEnforcer{
  val loginForm = controllers.forms.LoginForm()

  def new_ = HttpsAction { implicit request =>
    Ok(views.html.Session.new_(loginForm))
  }

  def delete = ActionInTransaction { (request: Request[AnyContent], connection: Connection) =>
    implicit val r = request

    gotoLogoutSucceeded.flashing(
      "success" -> "You have logged out"
    )
  }

  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) =>
    implicit val r = request
    implicit val c = connection

    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.Session.new_(formWithErrors)),
      user => gotoLoginSucceeded(user.withRegisteredEmail.get.id)
    )
  }
}
