package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.auth.Authorities.anyUser
import controllers.backend.{SessionBackend,UserBackend}
import models.{PotentialExistingUser,User}
import models.tables.Users

class SessionController @Inject() (
  sessionBackend: SessionBackend,
  userBackend: UserBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  private val loginForm = controllers.forms.LoginForm()
  private val registrationForm = controllers.forms.UserForm()
  private val NotAllowed = Left("forms.LoginForm.error.invalid_credentials")
  private val NotConfirmed = Left("forms.LoginForm.error.not_confirmed")

  private val m = views.Magic.scopedMessages("controllers.SessionController")

  def _new() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    // Beware: this has been copy/pasted to UserController, too
    request.user match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case _ => Ok(views.html.Session._new(loginForm, registrationForm))
    }
  }

  def delete = OptionallyAuthorizedAction(anyUser).async { implicit request =>
    val result = AuthResults.logoutSucceeded(request).flashing(
      "success" -> m("delete.success"),
      "event" -> "session-delete"
    )

    request.userSession match {
      case Some(session) => {
        for {
          _ <- sessionBackend.destroy(session.id)
        } yield result
      }
      case None => Future.successful(result)
    }
  }

  def create = Action.async { implicit request =>
    val boundForm = loginForm.bindFromRequest
    boundForm.fold(
      formWithErrors => Future.successful(BadRequest(views.html.Session._new(formWithErrors, registrationForm))),
      potentialExistingUser => {
        findUser(potentialExistingUser).flatMap(_ match {
          case Left(error) => {
            Future.successful(BadRequest(views.html.Session._new(boundForm.withGlobalError(error), registrationForm)))
          }
          case Right(user) => {
            for {
              _ <- sessionBackend.destroyExpiredSessionsForUserId(user.id)
              session <- sessionBackend.create(user.id, request.remoteAddress)
            } yield AuthResults.loginSucceeded(request, session).flashing("event" -> "session-create")
          }
        })
      }
    )
  }

  /** Finds a user matching the given credentials.
    *
    * Returns Left() on error. Possible errors:
    *
    * * The user does not exist or the password doesn't match (we don't leak which error this is).
    * * The user has not yet confirmed.
    */
  def findUser(potentialExistingUser: PotentialExistingUser): Future[Either[String, User]] = {
    userBackend.showByEmail(potentialExistingUser.email).map(_ match {
      case None => NotAllowed
      case Some(user) if !User.passwordMatchesHash(potentialExistingUser.password, user.passwordHash) => NotAllowed
      case Some(user) if user.confirmationToken.nonEmpty => NotConfirmed
      case Some(user) => Right(user)
    })
  }
}
