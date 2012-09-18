package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.LoginLogout
import play.api.mvc.{AnyContent,Controller,Request}

import models.OverviewUser

object SessionController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl with HttpsEnforcer {
  val loginForm = controllers.forms.LoginForm()
  val registrationForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.SessionController")

  def new_ = HttpsAction { implicit request =>
    Ok(views.html.Session.new_(loginForm, registrationForm))
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
      user => gotoLoginSucceeded(user.withRegisteredEmail.get.id).flashing("success" -> m("create.success"))
    )
  }
}
