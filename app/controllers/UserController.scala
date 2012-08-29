package controllers

import play.api.data.Form
import play.api.data.Forms.{email,nonEmptyText,mapping}
import play.api.data.validation.Constraint
import play.api.mvc.{Action,Controller}

import models.orm.User
import models.util.PasswordTester

object UserController extends Controller {
  val form = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure })
    )((email, password) => new User(email, password))((u: User) => Some(u.email, ""))
  )

  def new_() = Action { implicit request => Ok(views.html.User.new_(form)) }
  def create() = Action { Redirect(routes.Application.index()) }
}
