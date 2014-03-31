package controllers

import play.api.mvc.Controller

import controllers.auth.{ AuthorizedAction, Authorities }
import models.orm.stores.UserStore

trait TourController extends Controller {
  import Authorities.anyUser

  trait Storage {
    /** Sets the user at the given email address to _not_ see tooltips on the Tree/show page. */
    def disableTreeTooltipsForEmail(email: String) : Unit
  }

  def delete() = AuthorizedAction(anyUser) { implicit request =>
    storage.disableTreeTooltipsForEmail(request.user.email)
    NoContent
  }

  val storage: TourController.Storage
}

object TourController extends TourController {
  object DatabaseStorage extends Storage {
    override def disableTreeTooltipsForEmail(email: String) = {
      val user = UserStore.disableTreeTooltipsForEmail(email)
    }
  }

  override val storage = DatabaseStorage
}
