package controllers

import java.util.Date
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action,AnyContent,Request,RequestHeader}
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.{AuthResults,OptionallyAuthorizedAction}
import controllers.auth.Authorities.anyUser
import controllers.backend.SessionBackend
import mailers.Mailer
import models.User
import models.tables.Users

/**
 * Handles reset-password.
 *
 * RESTful methods:
 *
 * new: form to create a new reset-password process ("enter an email address")
 * create: (given an email address) creates a token and emails the user
 * edit: (given a token) edits the user's password
 * update: (given a token and password) changes the password, clears the token
 *         and logs in
 */
trait PasswordController extends Controller {
  private lazy val newForm = controllers.forms.NewPasswordForm()
  private lazy val editForm = controllers.forms.EditPasswordForm()
  private lazy val m = views.Magic.scopedMessages("controllers.PasswordController")

  def _new() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case None => Ok(views.html.Password._new(newForm))
      case Some(_) => doRedirect
    }
  }

  def edit(token: String) = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case Some(_) => doRedirect
      case None => {
        storage.findUserByResetToken(token) match {
          case None => showInvalidToken
          case Some(u) => Ok(views.html.Password.edit(u, editForm))
        }
      }
    }
  }

  private def doRedirect = Redirect(routes.WelcomeController.show)
  private def showInvalidToken(implicit request: Request[AnyContent]) = BadRequest(views.html.Password.editError())

  def create() = Action { implicit request =>
    newForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.Password._new(formWithErrors)), // no email address given
      email => {
        // We got a valid email address, but that doesn't mean this is a real
        // user. We need to send an email to the address either way.
        storage.findUserByEmail(email) match {
          case None => mail.sendCreateErrorUserDoesNotExist(email)
          case Some(user) => {
            // Success: generate a token and send an email
            val token = storage.addResetPasswordTokenToUser(user)
            mail.sendCreated(user.copy(resetPasswordToken=Some(token)))
          }
        }

        // Fake success either way
        doRedirect.flashing(
          "success" -> m("create.success", email),
          "event" -> "password-create"
        )
      }
    )
  }

  def update(token: String) = Action.async { implicit request =>
    storage.findUserByResetToken(token) match {
      case None => Future.successful(showInvalidToken)
      case Some(user) => {
        editForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(views.html.Password.edit(user, formWithErrors))),
          newPassword => {
            for {
              _ <- storage.resetPassword(user, newPassword)
              session <- sessionBackend.create(user.id, request.remoteAddress)
            } yield AuthResults.loginSucceeded(request, session).flashing(
              "success" -> m("update.success"),
              "event" -> "password-update"
            )
          }
        )
      }
    }
  }

  protected val sessionBackend: SessionBackend
  protected val storage: PasswordController.Storage
  protected val mail: PasswordController.Mail
}

object PasswordController extends PasswordController {
  override protected val sessionBackend = SessionBackend

  trait Storage {
    def findUserByEmail(email: String): Option[User]
    def findUserByResetToken(token: String): Option[User]
    def addResetPasswordTokenToUser(user: User): String
    def resetPassword(user: User, password: String): Future[Unit]
  }

  trait Mail {
    def sendCreated(user: User)(implicit request: RequestHeader) : Unit
    def sendCreateErrorUserDoesNotExist(email: String)(implicit request: RequestHeader) : Unit
  }

  val SecondsResetTokenIsValid = 14400 // 4 hours

  override protected val storage = new Storage with HasBlockingDatabase {
    import database.api._

    override def findUserByEmail(email: String) = {
      blockingDatabase.option(Users.filter(_.email === email))
    }

    override def findUserByResetToken(token: String) = {
      blockingDatabase.option(
        Users
          .filter(_.resetPasswordToken === token)
          .filter(_.resetPasswordSentAt >= new java.sql.Timestamp(new Date().getTime - SecondsResetTokenIsValid * 1000))
      )
    }

    override def addResetPasswordTokenToUser(user: User) = {
      val token = User.generateToken
      blockingDatabase.runUnit(
        Users
          .filter(_.id === user.id)
          .map(u => (u.resetPasswordToken, u.resetPasswordSentAt))
          .update((Some(token), Some(new java.sql.Timestamp(new Date().getTime))))
      )
      token
    }

    override def resetPassword(user: User, password: String) = {
      database.runUnit(
        Users
          .filter(_.id === user.id)
          .map(u => (u.passwordHash, u.resetPasswordToken, u.resetPasswordSentAt))
          .update((User.hashPassword(password), None, None))
      )
    }
  }

  override protected val mail = new Mail {
    override def sendCreated(user: User)(implicit request: RequestHeader) = {
      val mail = mailers.Password.create(user)
      mail.send
    }

    override def sendCreateErrorUserDoesNotExist(email: String)(implicit request: RequestHeader) = {
      val mail = mailers.Password.createErrorUserDoesNotExist(email)
      mail.send
    }
  }
}
