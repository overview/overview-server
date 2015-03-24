package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.backend.DocumentSetBackend
import controllers.forms.admin.{NewUserForm, EditUserForm}
import controllers.Controller
import models.{OverviewDatabase,User}
import models.orm.finders.{UserFinder, SessionFinder}
import models.orm.stores.{SessionStore, UserStore}
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.finders.ResultPage

trait UserController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  trait Storage {
    def findUser(email: String) : Option[User]
    def findUsers(page: Int) : ResultPage[User]
    def storeUser(user: User) : User // FIXME differentiate between INSERT and UPDATE
    def deleteUser(user: User) : Unit
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

  def show(email: String) = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    storage.findUser(email) match {
      case None => NotFound
      case Some(otherUser) => Ok(views.json.admin.User.show(otherUser))
    }
  }

  def update(email: String) = AuthorizedAction.inTransaction(adminUser) { implicit request =>
    if (email == request.user.email) {
      BadRequest
    } else {
      storage.findUser(email) match {
        case None => NotFound
        case Some(otherUser) => {
          EditUserForm(otherUser).bindFromRequest().fold(
            formWithErrors => BadRequest,
            updatedUser => {
              storage.storeUser(updatedUser)
              NoContent
            }
          )
        }
      }
    }
  }

  def delete(email: String) = AuthorizedAction(adminUser).async { implicit request =>
    if (email == request.user.email) {
      Future.successful(BadRequest)
    } else {
      storage.findUser(email) match {
        case None => Future.successful(NotFound)
        case Some(otherUser) => {
          documentSetBackend.countByUserEmail(email).map { nDocumentSets =>
            if (nDocumentSets == 0) {
              storage.deleteUser(otherUser)
              NoContent
            } else {
              BadRequest(m("delete.failure", email))
            }
          }
        }
      }
    }
  }

  val storage : UserController.Storage
}

object UserController extends UserController {
  object DatabaseStorage extends Storage {
    override def findUser(email: String) = OverviewDatabase.inTransaction { UserFinder.byEmail(email).headOption }
    override def findUsers(page: Int) = OverviewDatabase.inTransaction { ResultPage(UserFinder.all, PageSize, page) }
    override def storeUser(user: User) = OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
    override def deleteUser(user: User) = OverviewDatabase.inTransaction {
      import org.overviewproject.postgres.SquerylEntrypoint._
      import models.orm.Schema._
      SessionStore.delete(SessionFinder.byUserId(user.id).toQuery)
      UserStore.delete(user.id)
    }
  }

  override protected val documentSetBackend = DocumentSetBackend
  override val storage = DatabaseStorage
}
