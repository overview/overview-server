package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.orm.finders.DocumentSetFinder

trait CsvUploadController extends Controller {
  trait Storage {
    def countUserOwnedDocumentSets(user: String) : Long
  }

  def new_() = AuthorizedAction(anyUser) { implicit request =>
    val count = storage.countUserOwnedDocumentSets(request.user.email)

    Ok(views.html.CsvUpload.new_(request.user, count))
  }

  val storage : Storage
}

object CsvUploadController extends CsvUploadController {
  object DatabaseStorage extends CsvUploadController.Storage {
    override def countUserOwnedDocumentSets(owner: String) = {
      DocumentSetFinder.byOwner(owner).count
    }
  }

  override val storage = DatabaseStorage
}
