package controllers

import play.api.data.Form
import play.api.data.Forms.{text,mapping}
import play.api.mvc.{Action,Controller}

import models.orm.User

object ConfirmationController extends Controller {
  private def User__findByToken(token: String) = User.findByEmail(token)

  val form = Form(mapping(
    "token" -> text
    )(token => User__findByToken(token))((user: Option[User]) => user.map(_.email))
    // FIXME use User.findByToken() instead
  )

  def show(token: String) = Action { implicit request =>
    if (token.isEmpty) {
      Ok(views.html.Confirmation.index(form))
    } else {
      Redirect(routes.Application.index())
    }
  }
}
