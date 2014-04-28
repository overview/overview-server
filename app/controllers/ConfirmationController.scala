package controllers

import play.api.Logger
import play.api.mvc.Controller

import controllers.auth.{OptionallyAuthorizedAction,AuthResults}
import controllers.auth.Authorities.anyUser
import controllers.util.TransactionAction
import models.{MailChimp, OverviewUser}
import models.orm.Session
import models.orm.stores.SessionStore

object ConfirmationController extends Controller {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")

  /** Prompts for a confirmation token.
    */
  def index(email: String) = TransactionAction { implicit request =>
    Ok(views.html.Confirmation.index(email))
  }

  /** Confirms a confirmation token.
    *
    * Normally, there would be a POST update for confirming. However, we want
    * to confirm via email link, so it must be a GET.
    *
    * There are two possible outcomes:
    *
    * 1) The user is found
    */
  def show(token: String) = OptionallyAuthorizedAction(anyUser) { implicit request =>
    request.user match {
      case Some(user) => Redirect(routes.WelcomeController.show)
      case None => OverviewUser.findByConfirmationToken(token) match {
        case Some(u) => {
          val savedUser = u.confirm.save
          val session = Session(savedUser.id, request.remoteAddress)
          SessionStore.insertOrUpdate(session)

          if (u.requestedEmailSubscription) {
            MailChimp.subscribe(u.email).getOrElse(Logger.info(s"Did not attempt requested subscription for ${u.email}"))
          }

          AuthResults.loginSucceeded(request, session).flashing(
            "success" -> m("show.success"),
            "event" -> "confirmation-update"
          )
        }
        case None => BadRequest(views.html.Confirmation.show())
      }
    }
  }
}
