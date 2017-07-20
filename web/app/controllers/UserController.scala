package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.Configuration
import play.api.i18n.{MessagesApi,I18nSupport,Messages}
import play.api.data.Form
import play.api.mvc.{MessagesActionBuilder,RequestHeader}

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.database.exceptions.Conflict
import com.overviewdocs.models.UserRole
import controllers.auth.Authorities.anyUser
import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import mailers.Mailer
import models.{PotentialNewUser,PotentialExistingUser,User}
import models.tables.Users

class UserController @Inject() (
  configuration: Configuration,
  backendStuff: UserController.BackendStuff,
  messagesAction: MessagesActionBuilder,
  val controllerComponents: ControllerComponents,
  sessionNewHtml: views.html.Session._new
) extends BaseController {
  private val loginForm: Form[PotentialExistingUser] = controllers.forms.LoginForm()
  private val userForm: Form[PotentialNewUser] = controllers.forms.UserForm()
  private val authorizedToCreateUsers = configuration.get[Boolean]("overview.allow_registration")

  def _new() = optionallyAuthorizedAction(anyUser) { implicit request =>
    // Copy/pasted from SessionController
    val loginForm = controllers.forms.LoginForm()
    val registrationForm = controllers.forms.UserForm()
    request.user match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case _ => Ok(sessionNewHtml(loginForm, registrationForm))
    }
  }

  def create = messagesAction { implicit request =>
    if (!authorizedToCreateUsers) {
      AuthResults.authorizationFailed(request)  // returns 403
    } else {
      userForm.bindFromRequest().fold(
        formWithErrors => BadRequest(sessionNewHtml(loginForm, formWithErrors)),
        potentialNewUser => {
          backendStuff.findUserByEmail(potentialNewUser.email) match {
            case Some(u) => handleExistingUser(u)
            case None => handleNewUser(potentialNewUser)
          }
          Redirect(routes.ConfirmationController.index(potentialNewUser.email))
            .flashing("event" -> "user-create")
        })
    }
  }

  private def handleNewUser(potentialUser: PotentialNewUser)(implicit request: RequestHeader): Unit = {
    val user = try {
      val user = backendStuff.createUser(potentialUser)
      val url = routes.ConfirmationController.show(user.confirmationToken.get).absoluteURL()
      val contactUrl = configuration.get[String]("overview.contact_url")
      backendStuff.mailNewUser(user, url, contactUrl) // only if the previous line didn't generate an exception
    } catch {
      case _: Conflict => {
        /*
         * There's a duplicate key in the database. This means the email has
         * already been saved. But the only way to get to this code path is a
         * race: the email wasn't there when userForm.bindFromRequest was
         * called (it calls PotentialUser.apply, which does the lookup).
         *
         * If the process arrives here, some *other* thread of execution
         * *didn't*, and it's sending a confirmation email. We can just
         * pretend the database request went as planned, since from the
         * user's perspective, it did.
         */
      }
    }
  }

  private def handleExistingUser(user: User)(implicit request: RequestHeader): Unit = {
    val loginUrl = routes.SessionController._new().absoluteURL
    backendStuff.mailExistingUser(user, loginUrl)
  }
}

object UserController {
  @ImplementedBy(classOf[BlockingDatabaseBackendStuff])
  trait BackendStuff {
    /** Sends an email to an existing user saying someone tried to log in. */
    def mailExistingUser(user: User, loginUrl: String)(implicit messages: Messages): Unit

    /** Sends an email to a new user with a confirmation link. */
    def mailNewUser(user: User, url: String, contactUrl: String)(implicit messages: Messages): Unit

    /** Adds the user to the database, with a confirmation token, and returns it.
      *
      * @throws Conflict if there's a race and the user has recently been saved.
      */
    def createUser(user: PotentialNewUser): User

    def findUserByEmail(email: String): Option[User]
  }

  class BlockingDatabaseBackendStuff @Inject() (mailer: Mailer) extends BackendStuff with HasBlockingDatabase {
    import database.api._

    override def createUser(user: PotentialNewUser): User = {
      blockingDatabase.run(
        Users
          .map(_.createAttributes)
          .returning(Users)
          .+=(User.CreateAttributes(
            email=user.email,
            passwordHash=User.hashPassword(user.password),
            role=UserRole.NormalUser,
            confirmationToken=Some(User.generateToken),
            confirmationSentAt=Some(new java.sql.Timestamp(new java.util.Date().getTime)),
            resetPasswordToken=None,
            resetPasswordSentAt=None,
            lastActivityAt=None,
            lastActivityIp=None,
            emailSubscriber=user.emailSubscriber,
            treeTooltipsEnabled=true
          ))
      )
    }

    override def findUserByEmail(email: String) = {
      blockingDatabase.option(Users.filter(_.email === email))
    }

    override def mailNewUser(user: User, url: String, contactUrl: String)(implicit messages: Messages) = {
      val mail = mailers.User.create(user, url, contactUrl)
      mailer.send(mail)
    }

    override def mailExistingUser(user: User, loginUrl: String)(implicit messages: Messages) = {
      val mail = mailers.User.createErrorUserAlreadyExists(user, loginUrl)
      mailer.send(mail)
    }
  }
}
