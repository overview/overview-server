package controllers

import java.sql.Connection
import play.api.mvc.{AnyContent, Request, Controller}

import models.{OverviewUser, PotentialUser}

object UserController extends Controller with TransactionActionController {
  val loginForm = controllers.forms.LoginForm()
  val userForm = controllers.forms.UserForm()

  private val m = views.Magic.scopedMessages("controllers.UserController")

  def new_ = SessionController.new_

  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    userForm.bindFromRequest()(request).fold(
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

  private def handleExistingUser(user: OverviewUser)(implicit request: Request[AnyContent]) {
    mailers.User.createErrorUserAlreadyExists(user).send  
  }
  
  private def registerNewUser(user: PotentialUser)(implicit request: Request[AnyContent]) {
   val registeredUser = user.requestConfirmation
   registeredUser.save
   mailers.User.create(registeredUser).send
  }
}

