package controllers

import java.sql.Connection
import play.api.mvc.{AnyContent,Controller,Request}

import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.auth.Authorities.anyUser
import controllers.util.TransactionAction
import models.OverviewUser

object SessionController extends Controller {
  val loginForm = controllers.forms.LoginForm()
  val registrationForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.SessionController")

  def new_() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case _ => Ok(views.html.Session.new_(loginForm, registrationForm))
    }
  }

  def delete = TransactionAction { implicit request =>
    AuthResults.logoutSucceeded(request).flashing(
      "success" -> m("delete.success"),
      "event" -> "session-delete"
    )
  }

  def create = TransactionAction { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.Session.new_(formWithErrors, registrationForm)),
      user => {
        val recordedUser = user.withLoginRecorded(request.remoteAddress, new java.util.Date()).save
        AuthResults.loginSucceeded(request, user).flashing(
          "success" -> m("create.success"),
          "event" -> "session-create"
        )
      }
    )
  }
}
