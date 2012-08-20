package controllers

import java.sql.Connection
import play.api.mvc.{Action,AnyContent,Controller,Request}
import play.api.data.Form
import play.api.data.Forms._
import jp.t2v.lab.play20.auth.LoginLogout

import models.orm.User

object SessionController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl {
  val loginForm = Form {
    mapping(
      "email" -> email,
      "password" -> text)(User.authenticate)(_.map(u => (u.email, "")))
    .verifying("Invalid email or password", result => result.isDefined)
  }

  def new_ = Action { implicit request =>
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
      user => gotoLoginSucceeded(user.getOrElse(throw new Exception("invalid code")).id)
    )
  }
}
