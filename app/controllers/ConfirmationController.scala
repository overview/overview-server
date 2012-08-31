package controllers

import java.sql.Connection
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}

import models.{OverviewUser, ConfirmationRequest}


object ConfirmationController extends Controller with TransactionActionController {

  val form = Form {
     "token" -> nonEmptyText 
  }

  def show(token: String) = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    form.bindFromRequest()(request).fold(
      formWithErrors => BadRequest(views.html.Confirmation.index(formWithErrors)),
      token => {
        OverviewUser.findByConfirmationToken(token) match {
          case Some(u) => {
            u.confirm
            u.save
            Redirect(routes.Application.index())
          }
          case None => BadRequest(views.html.Confirmation.index(form)) // FIXME: indicate error
        }
      }
    )
  }
}
