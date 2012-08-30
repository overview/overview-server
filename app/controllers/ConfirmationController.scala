package controllers

import java.sql.Connection
import play.api.data.Form
import play.api.data.Forms.{nonEmptyText, mapping, text, tuple}
import play.api.mvc.{Action,AnyContent, Controller, Request}

import models.orm.{Schema, User}

object ConfirmationController extends Controller with TransactionActionController {

  val form = Form{
    mapping(
     "token" -> text 
    )(User.findByConfirmationToken)(u => u.get.confirmationToken)
    .verifying("No such token", user => user.isDefined)
  }

  def show(token: String) = ActionInTransaction { (request: Request[AnyContent], connection: Connection) => 
    implicit val r = request
    
    form.bindFromRequest()(request).fold(
      formWithErrors => BadRequest(views.html.Confirmation.index(formWithErrors)),
      user => {
        val confirmedUser = user.get.withConfirmation
        Schema.users.update(confirmedUser)
        Redirect(routes.Application.index())
      }
    )
  }
}
