package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.backend.UserBackend
import controllers.forms.admin.NewUserForm
import controllers.Controller
import models.{OverviewDatabase,User}
import models.orm.finders.{UserFinder, SessionFinder}
import models.orm.stores.{SessionStore, UserStore}
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.finders.ResultPage

trait UserController extends Controller {
  protected val backend: UserBackend

  trait Storage {
    def findUser(email: String) : Option[User]
    def findUsers(page: Int) : ResultPage[User]
    def storeUser(user: User) : User // FIXME differentiate between INSERT and UPDATE
  }

  private[admin] val PageSize = 50
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    Ok(views.html.admin.User.index(request.user))
  }

  def indexJson(page: Int) = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    val users = storage.findUsers(page)
    Ok(views.json.admin.User.index(users))
  }

  def create() = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    NewUserForm().bindFromRequest().fold(
      formWithErrors => BadRequest,
      newUser => {
        storage.findUser(newUser.email) match {
          case Some(_) => BadRequest
          case None => {
            val storedUser = storage.storeUser(newUser)
            Ok(views.json.admin.User.show(storedUser))
          }
        }
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

  val storage : UserController.Storage
}

object UserController extends UserController {
  object DatabaseStorage extends Storage {
    override def findUser(email: String) = OverviewDatabase.inTransaction { UserFinder.byEmail(email).headOption }
    override def findUsers(page: Int) = OverviewDatabase.inTransaction { ResultPage(UserFinder.all, PageSize, page) }
    override def storeUser(user: User) = OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
  }

  override protected val backend = UserBackend
  override val storage = DatabaseStorage
}
