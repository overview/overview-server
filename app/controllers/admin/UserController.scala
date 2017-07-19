package controllers.admin

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.backend.UserBackend
import controllers.forms.admin.NewUserForm
import models.User
import models.tables.Users
import models.pagination.PageRequest
import com.overviewdocs.database.exceptions.Conflict

class UserController @Inject() (
  backend: UserBackend,
  val controllerComponents: controllers.ControllerComponents,
  userIndexHtml: views.html.admin.User.index
) extends controllers.BaseController {

  private[admin] val PageSize = 50

  def index() = authorizedAction(adminUser) { implicit request =>
    Ok(userIndexHtml(request.user))
  }

  def indexJson(page: Int) = authorizedAction(adminUser).async { implicit request =>
    val pr = PageRequest((RequestData(request).getRequestedPageBase1 - 1) * PageSize, PageSize, false)
    for {
      page <- backend.indexPage(pr)
    } yield Ok(views.json.admin.User.index(page))
  }

  def create() = authorizedAction(adminUser).async { implicit request =>
    NewUserForm().bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        backend.create(attributes)
          .map { user: User => Ok(views.json.admin.User.show(user)) }
          .recover { case e: Conflict => BadRequest("conflict") }
      }
    )
  }

  def show(email: String) = authorizedAction(adminUser).async { implicit request =>
    backend.showByEmail(email).map(_ match {
      case None => NotFound
      case Some(otherUser) => Ok(views.json.admin.User.show(otherUser))
    })
  }

  def update(email: String) = authorizedAction(adminUser).async { implicit request =>
    if (email == request.user.email) {
      Future.successful(BadRequest)
    } else {
      import com.github.t3hnar.bcrypt._

      backend.showByEmail(email).flatMap(_ match {
        case None => Future.successful(NotFound)
        case Some(otherUser) => {
          val data: Map[String,String] = flatRequestData(request)

          val setAdminFuture = data.get("is_admin")
            .flatMap(_ match {
              case "true" => Some(true)
              case "false" => Some(false)
              case _ => None
            })
            .map(backend.updateIsAdmin(otherUser.id, _))
            .getOrElse(Future.unit)
          val setPasswordHashFuture = data.get("password")
            .map(s => backend.updatePasswordHash(otherUser.id, s.bcrypt(models.OverviewUser.BcryptRounds)))
            .getOrElse(Future.unit)

          for {
            _ <- setAdminFuture
            _ <- setPasswordHashFuture
          } yield NoContent
        }
      })
    }
  }

  def delete(email: String) = authorizedAction(adminUser).async { implicit request =>
    if (email == request.user.email) {
      Future.successful(BadRequest("You cannot delete yourself"))
    } else {
      backend.showByEmail(email).flatMap(_ match {
        case None => Future.successful(NotFound)
        case Some(otherUser) => {
          backend.destroy(otherUser.id).map(_ => NoContent)
        }
      })
    }
  }
}
