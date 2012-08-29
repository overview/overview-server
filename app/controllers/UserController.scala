package controllers

import java.sql.Connection
import models.orm.User
import models.util.PasswordTester
import play.api.data.Form
import play.api.data.Forms.{email,nonEmptyText,mapping}
import play.api.data.validation.Constraint
import play.api.mvc.{Action, AnyContent, Controller, Request}




object UserController extends Controller with TransactionActionController {
  val form = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure })
    )((email, password) => User.prepareNewRegistration(email, password))
     ((u: User) => Some(u.email, ""))
  )

  def new_() = Action { implicit request => Ok(views.html.User.new_(form)) }
  
  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    form.bindFromRequest()(request).fold(
      f => Redirect(routes.UserController.new_),
      user => {
        User.findByEmail(user.email) match {
          case Some(_) => handleExistingUser(user)
          case None => registerNewUser(user)
        }
        Redirect(routes.Application.index).
          flashing("success" -> "Check your email for confirmation link")
      }
    )
  }

  private def handleExistingUser(user: User) {
    
  }
  
  private def registerNewUser(user: User) {
    
   user.save
  }
  
  private def sendConfirmationEmail(email: String) {}
  private def sendAccountExistsEmail(email: String) {}
  
}

