package controllers

import java.sql.Connection
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.Logger
import play.api.mvc.{Action,AnyContent, Controller, Request}
import controllers.auth.AuthResults
import controllers.util.TransactionAction
import models.{ MailChimp, OverviewUser }


object ConfirmationController extends Controller {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")

  private val form = forms.ConfirmationForm()

  /** Prompts for or confirms a confirmation token.
    *
    * Normally, there would be a POST update for confirming. However, we want
    * to confirm via email link, so it must be a GET, so both are handled in
    * the same action.
    */
  def show(token: String) = TransactionAction { implicit request =>
    form.bindFromRequest()(request).fold(
      formWithErrors => {
        if (formWithErrors("token").value.getOrElse("").length > 0) {
          // The user entered text, and it was wrong
          BadRequest(views.html.Confirmation.index(formWithErrors))
        } else {
          // The user browsed to this page without entering anything
          Ok(views.html.Confirmation.index(form))
        }
      },
      u => {
        u.confirm.withLoginRecorded(request.remoteAddress, new java.util.Date()).save
        if (u.requestedEmailSubscription) MailChimp.subscribe(u.email).getOrElse(Logger.info(s"Did not attempt requested subscription for ${u.email}"))
        
        AuthResults.loginSucceeded(request, u).flashing(
          "success" -> m("show.success"),
          "event" -> "confirmation-update"
        )
      }
    )
  }
}

