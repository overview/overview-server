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
    )((email, password) => new User(email, password))((u: User) => Some(u.email, ""))
  )

  def new_() = Action { implicit request => Ok(views.html.User.new_(form)) }
  
  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    form.bindFromRequest()(request).fold(
      newUser => {
        val email = newUser.data("email")
        val password = newUser.data("password")
        createNewUser(email, password)
        sendConfirmationEmail(email)
        
        Redirect(routes.DocumentSetController.index()).
          flashing(("success" -> "check email for confirmation link"))
      },
      user => {
        sendAccountExistsEmail(user.email)
        Redirect(routes.SessionController.new_)
      }
    )
  }

  private def createNewUser(email: String, password: String) {
   // User(email, password)
  }
  
  private def sendConfirmationEmail(email: String) {}
  private def sendAccountExistsEmail(email: String) {}
  
}

