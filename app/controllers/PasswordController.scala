package controllers

import java.util.Date
import java.sql.Connection
import play.api.mvc.{AnyContent,Controller,Request}

import controllers.auth.{AuthResults,OptionallyAuthorizedAction}
import controllers.auth.Authorities.anyUser
import controllers.util.TransactionAction
import mailers.Mailer
import models.{OverviewUser,ResetPasswordRequest}

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
    request.user.map(userAlreadyLoggedIn => doRedirect).getOrElse({
      Ok(views.html.Password.new_(newForm))
    })
  }

  def edit(token: String) = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user.map(userAlreadyLoggedIn => doRedirect).getOrElse({
      tokenToUser(token).map({ user =>
        Ok(views.html.Password.edit(user, editForm))
      }).getOrElse(showInvalidToken)
    })
  }

  private def doRedirect = Redirect(routes.WelcomeController.show)
  private def showInvalidToken(implicit request: Request[AnyContent]) = BadRequest(views.html.Password.editError())

  def create() = TransactionAction { implicit request =>
    newForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.Password.new_(formWithErrors)), // no email address given
      email => {
        // We got a valid email address, but that doesn't mean this is a real
        // user. We need to send an email to the address either way.
        emailToUser(email).map({ user =>
          // Success: generate a token and send an email
          val userWithRequest = user.withResetPasswordRequest
          userWithRequest.save
          sendMail(mailers.Password.create(userWithRequest))
        }).getOrElse({
          // Failure: notify the email address
          sendMail(mailers.Password.createErrorUserDoesNotExist(email))
        })

        // Fake success either way
        doRedirect.flashing("success" -> m("create.success", email))
      }
    )
  }

  def update(token: String) = TransactionAction { implicit request =>
    tokenToUser(token).map({ user =>
      editForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.Password.edit(user, editForm)),
        newPassword => {
          val userWithNewPassword = user
            .withNewPassword(newPassword)
            .withLoginRecorded(request.remoteAddress, new java.util.Date())
            .save
          AuthResults.loginSucceeded(request, userWithNewPassword).flashing("success" -> m("update.success"))
        }
      )
    }).getOrElse(showInvalidToken)
  }

  protected def sendMail(mail: Mailer): Unit
  protected def emailToUser(email: String): Option[OverviewUser]
  protected def tokenToUser(token: String): Option[OverviewUser with ResetPasswordRequest]
}

object PasswordController extends PasswordController {
  val SecondsResetTokenIsValid = 14400 // 4 hours

  override protected def emailToUser(email: String) = {
    OverviewUser.findByEmail(email)
  }

  override protected def tokenToUser(token: String) = {
    OverviewUser.findByResetPasswordTokenAndMinDate(
      token,
      new Date((new Date()).getTime - SecondsResetTokenIsValid * 1000)
    )
  }

  override protected def sendMail(mail: Mailer) = mail.send
}
