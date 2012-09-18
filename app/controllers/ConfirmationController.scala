package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.LoginLogout
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}

import models.OverviewUser

object ConfirmationController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl {
  private val m = views.Magic.scopedMessages("controllers.ConfirmationController")

  val form = Form { mapping(
   "token" -> text.verifying(OverviewUser.findByConfirmationToken(_).isDefined)
   )(OverviewUser.findByConfirmationToken(_).
     getOrElse(throw new Exception("User already confirmed")))(u => Some(u.confirmationToken))
  }

  def show(token: String) = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    form.bindFromRequest()(request).fold(
      formWithErrors => BadRequest(views.html.Confirmation.index(formWithErrors)),
      u => {
        u.confirm.save
        gotoLoginSucceeded(u.id).flashing("success" -> m("show.success"))
      }
    )
  }
}

