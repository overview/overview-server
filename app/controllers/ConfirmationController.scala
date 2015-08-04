package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import scala.concurrent.Future

import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.auth.Authorities.anyUser
import controllers.backend.SessionBackend
import models.{IntercomConfiguration, MailChimp, OverviewUser}
import models.orm.stores.UserStore
import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.util.Logger

object ConfirmationController extends Controller {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")
  private val logger = Logger.forClass(getClass)

  private val sessionBackend = SessionBackend

  /** Prompts for a confirmation token.
    */
  def index(email: String) = Action { implicit request =>
    Ok(views.html.Confirmation.index(email))
  }

  /** Confirms a confirmation token.
    *
    * Normally, there would be a POST update for confirming. However, we want
    * to confirm via email link, so it must be a GET.
    */
  def show(token: String) = OptionallyAuthorizedAction(anyUser).async { implicit request =>
    request.user match {
      case Some(user) => Future.successful(Redirect(routes.WelcomeController.show))
      case None => OverviewUser.findByConfirmationToken(token) match {
        case None => Future.successful(BadRequest(views.html.Confirmation.show()))
        case Some(u) => {
          val savedUser = DeprecatedDatabase.inTransaction { OverviewUser(UserStore.insertOrUpdate(u.confirm.toUser)) }

          for {
            session <- sessionBackend.create(savedUser.id, request.remoteAddress)
          } yield {
            if (u.requestedEmailSubscription) {
              MailChimp.subscribe(u.email).getOrElse(logger.info(s"Did not attempt requested subscription for ${u.email}"))
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
