package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.LoginLogout
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}

import models.OverviewUser

object ConfirmationController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")

  private val form = forms.ConfirmationForm()

  def show(token: String) = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request

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
        u.confirm.save
        gotoLoginSucceeded(u.id).flashing("success" -> m("show.success"))
      }
    )
  }
}

