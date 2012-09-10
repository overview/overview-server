package controllers

import java.sql.Connection
import mailers.User.create
import models.{OverviewUser, PotentialUser}
import models.util.PasswordTester
import play.api.data.Form
import play.api.data.Forms.{email,nonEmptyText,mapping}
import play.api.data.validation.Constraint
import play.api.mvc.{Action, AnyContent, Controller, Request}




object UserController extends Controller with TransactionActionController with HttpsEnforcer {
  val form = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure })
    )(PotentialUser)(u => Some(u.email, u.password))
  )

  def new_() = HttpsAction { implicit request => Ok(views.html.User.new_(form)) }
  
  def create = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    form.bindFromRequest()(request).fold(
      formWithErrors => BadRequest(views.html.User.new_(formWithErrors)),
      user => {
        user.withRegisteredEmail match {
          case Some(u) => handleExistingUser(u)
          case None => registerNewUser(user)
        }
        Redirect(routes.ConfirmationController.show("")).
          flashing("success" -> "Check your email for confirmation link")
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
  
  private def sendConfirmationEmail(email: String) {}
  private def sendAccountExistsEmail(email: String) {}
  
}

