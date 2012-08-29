package controllers

import java.sql.Connection
import models.orm.User
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Controller, Request}

object UserController extends Controller with TransactionActionController {

  val loginForm = Form {
    mapping(
      "email" -> email,
      "password" -> text
    )(User.prepareNewRegistration)((user: User) => Some(user.email, "")).
    verifying("Already existing account", u => !User.isConfirmed(u.email))
  }
  
  def new_ = Action { implicit request =>
Ok("hi")
    //	Ok(views.html.User.new_(loginForm))
  }
  
  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    loginForm.bindFromRequest()(request).fold(
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