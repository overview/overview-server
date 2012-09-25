package controllers.admin

import java.sql.Connection
import play.api.mvc.{AnyContent,Request}

import controllers.forms.AdminUserForm
import models.orm.User

object UserController extends AdminController {
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = adminAction((user: User) => authorizedIndex(user)(_: Request[AnyContent], _: Connection))
  def update(id: Long) = adminAction((user: User) => authorizedUpdate(user, id)(_: Request[AnyContent], _: Connection))
  def delete(id: Long) = adminAction((user: User) => authorizedDelete(user, id)(_: Request[AnyContent], _: Connection))

  def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    val users = User.all.toSeq
    Ok(views.html.admin.User.index(user, users))
  }

  def authorizedUpdate(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    User.findById(id).map({ otherUser =>
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

  def authorizedDelete(user: User, id: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    User.findById(id).map({ otherUser =>
      if (otherUser.id == user.id) {
        BadRequest
      } else {
        otherUser.delete
        Redirect(routes.UserController.index()).
          flashing("success" -> m("delete.success", otherUser.email))
      }
    }).getOrElse(NotFound)
  }
}
