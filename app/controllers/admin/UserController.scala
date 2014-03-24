package controllers.admin

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import controllers.forms.admin.{NewUserForm, EditUserForm}
import models.orm.User
import models.orm.finders.{ UserFinder, DocumentSetUserFinder }
import models.orm.stores.UserStore
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.finders.ResultPage

trait UserController extends Controller {
  trait Storage {
    def findUser(email: String) : Option[User]
    def findUsers(page: Int) : ResultPage[User]
    def countDocumentSetsForEmail(email: String) : Long
    def storeUser(user: User) : User // FIXME differentiate between INSERT and UPDATE
    def deleteUser(user: User) : Unit
  }

  private[admin] val PageSize = 50
  private val m = views.Magic.scopedMessages("controllers.admin.UserController")

  def index() = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.User.index(request.user))
  }

  def indexJson(page: Int) = AuthorizedAction(adminUser) { implicit request =>
    val users = storage.findUsers(page)
    Ok(views.json.admin.User.index(users))
  }

  def create() = AuthorizedAction(adminUser) { implicit request =>
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

  def update(email: String) = AuthorizedAction(adminUser) { implicit request =>
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

  def delete(email: String) = AuthorizedAction(adminUser) { implicit request =>
    if (email == request.user.email) {
      BadRequest
    } else {
      storage.findUser(email) match {
        case None => NotFound
        case Some(otherUser) => {
          if (storage.countDocumentSetsForEmail(email) == 0) {
            storage.deleteUser(otherUser)
            NoContent
          } else {
            BadRequest(m("delete.failure", email))
          }
        }
      }
    }
  }

  val storage : UserController.Storage
}

object UserController extends UserController {
  object DatabaseStorage extends Storage {
    override def findUser(email: String) = UserFinder.byEmail(email).headOption
    override def findUsers(page: Int) = ResultPage(UserFinder.all, PageSize, page)
    override def storeUser(user: User) = UserStore.insertOrUpdate(user)
    override def deleteUser(user: User) = {
      import org.overviewproject.postgres.SquerylEntrypoint._
      UserStore.delete(user.id)
    }
    override def countDocumentSetsForEmail(email: String) = {
      DocumentSetUserFinder
        .byUserAndRole(email, Ownership.Owner)
        .exceptDeletedDocumentSets
        .count
    }
  }

  override val storage = DatabaseStorage
}
