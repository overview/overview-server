package controllers

import play.api.mvc.{Controller, RequestHeader}

import controllers.util.TransactionAction
import models.{OverviewUser, PotentialUser}

object UserController extends Controller {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.UserController")

  def new_ = SessionController.new_

  def create = TransactionAction { implicit request =>
    userForm.bindFromRequest().fold(
      formWithErrors => BadRequest(views.html.Session.new_(loginForm, formWithErrors)),
      user => {
        user.withRegisteredEmail match {
          case Some(u) => handleExistingUser(u)
          case None => registerNewUser(user)
        }
        Redirect(routes.ConfirmationController.show("")).
          flashing("success" -> m("create.success"))
      }
    )
  }

  private def handleExistingUser(user: OverviewUser)(implicit request: RequestHeader) {
    mailers.User.createErrorUserAlreadyExists(user).send  
  }
  
  private def registerNewUser(user: PotentialUser)(implicit request: RequestHeader) {
   val registeredUser = user.requestConfirmation
   registeredUser.save
   mailers.User.create(registeredUser).send
  }
}

