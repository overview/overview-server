package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.backend.UserBackend
import controllers.forms.admin.NewUserForm
import controllers.Controller
import models.User
import models.tables.Users
import models.pagination.Page
import org.overviewproject.database.exceptions.Conflict

trait UserController extends Controller {
  protected val backend: UserBackend

  private[admin] val PageSize = 50
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.User.index(request.user))
  }

  def indexJson(page: Int) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      page <- backend.indexPage(pageRequest(request, PageSize))
    } yield Ok(views.json.admin.User.index(page))
  }

  def create() = AuthorizedAction(adminUser).async { implicit request =>
    NewUserForm().bindFromRequest().fold(
      formWithErrors => Future.successful(BadRequest),
      attributes => {
        backend.create(attributes)
          .map { user: User => Ok(views.json.admin.User.show(user)) }
          .recover { case e: Conflict => BadRequest("conflict") }
      }
    )
  }

  def show(email: String) = AuthorizedAction(adminUser).async { implicit request =>
    backend.showByEmail(email).map(_ match {
      case None => NotFound
      case Some(otherUser) => Ok(views.json.admin.User.show(otherUser))
    })
  }

  def update(email: String) = AuthorizedAction(adminUser).async { implicit request =>
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
            .getOrElse(Future.successful(()))
          val setPasswordHashFuture = data.get("password")
            .map(s => backend.updatePasswordHash(otherUser.id, s.bcrypt(models.OverviewUser.BcryptRounds)))
            .getOrElse(Future.successful(()))

          for {
            _ <- setAdminFuture
            _ <- setPasswordHashFuture
          } yield NoContent
        }
      })
    }
  }

  def delete(email: String) = AuthorizedAction(adminUser).async { implicit request =>
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

object UserController extends UserController {
  override protected val backend = UserBackend
}
