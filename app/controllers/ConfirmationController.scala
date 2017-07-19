package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.MessagesActionBuilder
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.Authorities.anyUser
import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.backend.SessionBackend
import models.{IntercomConfiguration,MailChimp,User}
import models.tables.Users

class ConfirmationController @Inject() (
  sessionBackend: SessionBackend,
  mailChimp: MailChimp,
  messagesAction: MessagesActionBuilder,
  val controllerComponents: ControllerComponents,
  confirmationIndexHtml: views.html.Confirmation.index,
  confirmationShowHtml: views.html.Confirmation.show
) extends BaseController with HasBlockingDatabase {
  /** Prompts for a confirmation token.
    */
  def index(email: String) = messagesAction { implicit request =>
    Ok(confirmationIndexHtml(email))
  }

  private def findUserByConfirmationToken(token: String): Option[User] = {
    import database.api._
    blockingDatabase.option(Users.filter(_.confirmationToken === token))
  }

  private def confirmUser(user: User): Future[Unit] = {
    import database.api._
    database.runUnit(
      Users
        .filter(_.id === user.id)
        .map(u => (u.confirmationToken, u.confirmedAt))
        .update((None, Some(new java.sql.Timestamp(new java.util.Date().getTime()))))
    )
  }

  /** Confirms a confirmation token.
    *
    * Normally, there would be a POST update for confirming. However, we want
    * to confirm via email link, so it must be a GET.
    */
  def show(token: String) = optionallyAuthorizedAction(anyUser).async { implicit request =>
    request.user match {
      case Some(user) => Future.successful(Redirect(routes.WelcomeController.show))
      case None => findUserByConfirmationToken(token) match {
        case None => Future.successful(BadRequest(confirmationShowHtml()))
        case Some(unconfirmedUser) => {
          for {
            _ <- confirmUser(unconfirmedUser)
            session <- sessionBackend.create(unconfirmedUser.id, request.remoteAddress)
          } yield {
            if (unconfirmedUser.emailSubscriber) {
              mailChimp.subscribeInBackground(unconfirmedUser.email)
            }

            AuthResults
              .loginSucceeded(request, session)
              .flashing(
                "success" -> request.messages("controllers.ConfirmationController.show.success"),
                "event" -> "confirmation-update"
              )
          }
        }
      }
    }
  }
}
