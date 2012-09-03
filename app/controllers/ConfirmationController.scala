package controllers

import java.sql.Connection
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}

import models.{OverviewUser, ConfirmationRequest}


object ConfirmationController extends Controller with TransactionActionController {

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
        Redirect(routes.Application.index())
      }
    )
  }
}
