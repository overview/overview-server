package controllers

import java.util.Date
import java.sql.Connection
import play.api.mvc.{AnyContent,Controller,Request}

import controllers.auth.LoginLogout
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
trait PasswordController extends BaseController with LoginLogout {
  private lazy val newForm = controllers.forms.NewPasswordForm()
  private lazy val editForm = controllers.forms.EditPasswordForm()
  private lazy val m = views.Magic.scopedMessages("controllers.PasswordController")

  def new_() = optionallyAuthorizedAction({ user: Option[OverviewUser] => optionallyAuthorizedNew_(user)(_: Request[AnyContent], _: Connection)})
  def edit(token: String) = optionallyAuthorizedAction({ user: Option[OverviewUser] => optionallyAuthorizedEdit(user, token)(_: Request[AnyContent], _: Connection)})
  def create() = ActionInTransaction { doCreate()(_: Request[AnyContent], _: Connection) }
  def update(token: String) = ActionInTransaction { doUpdate(token)(_: Request[AnyContent], _: Connection) }

  private def doRedirect = Redirect(routes.WelcomeController.show)
  private def showInvalidToken(implicit request: Request[AnyContent]) = BadRequest(views.html.Password.editError())

  private[controllers] def optionallyAuthorizedNew_(optionalUser: Option[OverviewUser])(implicit request: Request[AnyContent], connection: Connection) = {
    optionalUser.map(userAlreadyLoggedIn => doRedirect).getOrElse({
      Ok(views.html.Password.new_(newForm))
    })
  }

  private[controllers] def doCreate()(implicit request: Request[AnyContent], connection: Connection) = {
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

  private[controllers] def optionallyAuthorizedEdit(optionalUser: Option[OverviewUser], token: String)(implicit request: Request[AnyContent], connection: Connection) = {
    optionalUser.map(userAlreadyLoggedIn => doRedirect).getOrElse({
      tokenToUser(token).map({ user =>
        Ok(views.html.Password.edit(user, editForm))
      }).getOrElse(showInvalidToken)
    })
  }

  private[controllers] def doUpdate(token: String)(implicit request: Request[AnyContent], connection: Connection) = {
    tokenToUser(token).map({ user =>
      editForm.bindFromRequest.fold(
        formWithErrors => BadRequest(views.html.Password.edit(user, editForm)),
        newPassword => {
          val userWithNewPassword = user
            .withNewPassword(newPassword)
            .withLoginRecorded(request.remoteAddress, new java.util.Date())
            .save
          gotoLoginSucceeded(userWithNewPassword.id).flashing("success" -> m("update.success"))
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
