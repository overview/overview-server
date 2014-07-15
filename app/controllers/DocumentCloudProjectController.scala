package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.orm.finders.DocumentSetFinder

trait DocumentCloudProjectController extends Controller {
  trait Storage {
    def countUserOwnedDocumentSets(user: String) : Long
  }

  def index = AuthorizedAction.inTransaction(anyUser) { implicit request =>
    val count = storage.countUserOwnedDocumentSets(request.user.email)

    Ok(views.html.DocumentCloudProject.index(request.user, count))
  }

  val storage : Storage
}

object DocumentCloudProjectController extends DocumentCloudProjectController {
  object DatabaseStorage extends DocumentCloudProjectController.Storage {
    override def countUserOwnedDocumentSets(owner: String) = {
      DocumentSetFinder.byOwner(owner).count
    }
  }

  override val storage = DatabaseStorage
}
