package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.util.Logger
import controllers.auth.Authorities.anyUser
import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.backend.SessionBackend
import models.{IntercomConfiguration,MailChimp,User}
import models.tables.Users

object ConfirmationController extends Controller with HasBlockingDatabase {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")
  private val logger = Logger.forClass(getClass)

  private val sessionBackend = SessionBackend

  /** Prompts for a confirmation token.
    */
  def index(email: String) = Action { implicit request =>
    Ok(views.html.Confirmation.index(email))
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
  def show(token: String) = OptionallyAuthorizedAction(anyUser).async { implicit request =>
    request.user match {
      case Some(user) => Future.successful(Redirect(routes.WelcomeController.show))
      case None => findUserByConfirmationToken(token) match {
        case None => Future.successful(BadRequest(views.html.Confirmation.show()))
        case Some(unconfirmedUser) => {
          for {
            _ <- confirmUser(unconfirmedUser)
            session <- sessionBackend.create(unconfirmedUser.id, request.remoteAddress)
          } yield {
            if (unconfirmedUser.emailSubscriber) {
              MailChimp.subscribe(unconfirmedUser.email)
                .getOrElse(logger.info(s"Did not attempt requested subscription for ${unconfirmedUser.email}"))
            }

            AuthResults
              .loginSucceeded(request, session)
              .flashing(
                "success" -> m("show.success"),
                "event" -> "confirmation-update"
              )
          }
        }
      }
    }
  }
}
