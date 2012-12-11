package controllers.admin

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.forms.AdminUserForm
import models.OverviewUser

object UserController extends Controller {
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = AuthorizedAction(adminUser) { implicit request =>
    val users = OverviewUser.all.toSeq
    Ok(views.html.admin.User.index(request.user, users))
  }

  def update(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    OverviewUser.findById(id).map({ otherUser =>
      AdminUserForm(otherUser).bindFromRequest().fold(
        formWithErrors => BadRequest,
        updatedUser => {
          updatedUser.save
          Redirect(routes.UserController.index()).
            flashing("success" -> m("update.success", otherUser.email))
        }
      )
    }).getOrElse(NotFound)
  }

  def delete(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    OverviewUser.findById(id).map({ otherUser =>
      if (otherUser.id == request.user.id) {
        BadRequest
      } else {
        otherUser.delete
        Redirect(routes.UserController.index()).
          flashing("success" -> m("delete.success", otherUser.email))
      }
    }).getOrElse(NotFound)
  }
}
