package controllers

import java.sql.Connection
import jp.t2v.lab.play20.auth.LoginLogout
import models.{OverviewUser, ConfirmationRequest}
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}




object ConfirmationController extends Controller with TransactionActionController with LoginLogout with AuthConfigImpl {

  val form = Form { mapping(
   "token" -> nonEmptyText 
   )(OverviewUser.findByConfirmationToken)(_.map(_.confirmationToken))
   .verifying("Token not found", u => u.isDefined)
  }

  def show(token: String) = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    form.bindFromRequest()(request).fold(
      formWithErrors => BadRequest(views.html.Confirmation.index(formWithErrors)),
      user => {
        user.map(_.confirm.save)
        gotoLoginSucceeded(user.get.id).flashing(
          "success" -> "Your registration is confirmed and you have logged in")
      }
    )
  }
}
