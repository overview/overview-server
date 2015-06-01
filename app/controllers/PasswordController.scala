package controllers

import java.util.Date
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action,AnyContent,Request,RequestHeader}
import scala.concurrent.Future

import controllers.auth.{AuthResults,OptionallyAuthorizedAction}
import controllers.auth.Authorities.anyUser
import controllers.backend.SessionBackend
import mailers.Mailer
import models.{OverviewDatabase,OverviewUser,ResetPasswordRequest,User}
import models.orm.stores.{UserStore}

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

  def new_() = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case None => Ok(views.html.Password.new_(newForm))
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
      formWithErrors => BadRequest(views.html.Password.new_(formWithErrors)), // no email address given
      email => {
        // We got a valid email address, but that doesn't mean this is a real
        // user. We need to send an email to the address either way.
        storage.findUserByEmail(email) match {
          case None => mail.sendCreateErrorUserDoesNotExist(email)
          case Some(user) => {
            // Success: generate a token and send an email
            val userWithRequest = user.withResetPasswordRequest
            OverviewDatabase.inTransaction {
              storage.insertOrUpdateUser(userWithRequest.toUser)
            }
            mail.sendCreated(userWithRequest)
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
            val savedUser = OverviewDatabase.inTransaction {
              storage.insertOrUpdateUser(user.withNewPassword(newPassword).toUser)
            }

            for {
              session <- sessionBackend.create(savedUser.id, request.remoteAddress)
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
    def findUserByEmail(email: String) : Option[OverviewUser]
    def findUserByResetToken(token: String) : Option[OverviewUser with ResetPasswordRequest]
    def insertOrUpdateUser(user: User) : User
  }

  trait Mail {
    def sendCreated(user: OverviewUser with ResetPasswordRequest)(implicit request: RequestHeader) : Unit
    def sendCreateErrorUserDoesNotExist(email: String)(implicit request: RequestHeader) : Unit
  }

  val SecondsResetTokenIsValid = 14400 // 4 hours

  override protected val storage = new Storage {
    override def findUserByEmail(email: String) = {
      OverviewUser.findByEmail(email)
    }

    override def findUserByResetToken(token: String) = {
      OverviewUser.findByResetPasswordTokenAndMinDate(
        token,
        new Date((new Date()).getTime - SecondsResetTokenIsValid * 1000)
      )
    }

    override def insertOrUpdateUser(user: User) = UserStore.insertOrUpdate(user)
  }

  override protected val mail = new Mail {
    override def sendCreated(userWithRequest: OverviewUser with ResetPasswordRequest)(implicit request: RequestHeader) = {
      val mail = mailers.Password.create(userWithRequest)
      mail.send
    }

    override def sendCreateErrorUserDoesNotExist(email: String)(implicit request: RequestHeader) = {
      val mail = mailers.Password.createErrorUserDoesNotExist(email)
      mail.send
    }
  }
}
