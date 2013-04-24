package controllers.admin

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.forms.AdminUserForm
import models.OverviewUser
import models.orm.User
import models.orm.finders.{ UserFinder, DocumentSetFinder }
import models.orm.stores.UserStore

object UserController extends Controller {
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = AuthorizedAction(adminUser) { implicit request =>
    val users = OverviewUser.all.toSeq
    Ok(views.html.admin.User.index(request.user, users))
  }

  def update(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    UserFinder.byId(id).headOption.map({ otherUser =>
      AdminUserForm(OverviewUser(otherUser)).bindFromRequest().fold(
        formWithErrors => BadRequest,
        updatedUser => {
          updatedUser.save
          Redirect(routes.UserController.index()).
            flashing("success" -> m("update.success", otherUser.email))
        })
    }).getOrElse(NotFound)
  }

  def delete(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    UserFinder.byId(id).headOption.map({ otherUser =>
      if (otherUser.id == request.user.id) {
        BadRequest
      } else {
        if (DocumentSetFinder.byOwner(otherUser.email).count == 0) {
          import org.overviewproject.postgres.SquerylEntrypoint._
          UserStore.delete(otherUser.id)
          Redirect(routes.UserController.index())
            .flashing("success" -> m("delete.success", otherUser.email))
        }
        else {
          Redirect(routes.UserController.index())
            .flashing("error" -> m("delete.failure", otherUser.email))
        }
      }
    }).getOrElse(NotFound)
  }
}
