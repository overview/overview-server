package controllers

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.{AuthorizedAction,Authorities}
import models.tables.Users

trait TourController extends Controller {
  def delete() = AuthorizedAction(Authorities.anyUser) { implicit request =>
    storage.disableTreeTooltipsForEmail(request.user.email)
    NoContent
  }

  val storage: TourController.Storage
}

object TourController extends TourController {
  trait Storage {
    /** Sets the user at the given email address to _not_ see tooltips on the Tree/show page. */
    def disableTreeTooltipsForEmail(email: String) : Unit
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def disableTreeTooltipsForEmail(email: String) = {
      blockingDatabase.runUnit(Users.filter(_.email === email).map(_.treeTooltipsEnabled).update(false))
    }
  }

  override val storage = DatabaseStorage
}
