package controllers

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi

import com.overviewdocs.database.HasBlockingDatabase
import controllers.auth.{AuthorizedAction,Authorities}
import models.tables.Users

class TourController @Inject() (
  storage: TourController.Storage,
  val controllerComponents: ControllerComponents
) extends BaseController {
  def delete() = authorizedAction(Authorities.anyUser) { implicit request =>
    storage.disableTreeTooltipsForEmail(request.user.email)
    NoContent
  }
}

object TourController {
  @ImplementedBy(classOf[TourController.DatabaseStorage])
  trait Storage {
    /** Sets the user at the given email address to _not_ see tooltips on the Tree/show page. */
    def disableTreeTooltipsForEmail(email: String) : Unit
  }

  class DatabaseStorage @Inject() extends Storage with HasBlockingDatabase {
    import database.api._

    override def disableTreeTooltipsForEmail(email: String) = {
      blockingDatabase.runUnit(Users.filter(_.email === email).map(_.treeTooltipsEnabled).update(false))
    }
  }
}
