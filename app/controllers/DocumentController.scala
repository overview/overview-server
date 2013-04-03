package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocument
import models.orm.finders.DocumentFinder
import models.OverviewDocument

trait DocumentController extends Controller {
  trait Storage {
    def find(id: Long) : Option[OverviewDocument]
  }

  def showJson(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)) { implicit request =>
    storage.find(documentId) match {
      case Some(document) => Ok(views.json.Document.show(document))
      case None => NotFound
    }
  }

  def show(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)) { implicit request =>
    storage.find(documentId) match {
      case Some(document) => Ok(views.html.Document.show(document))
      case None => NotFound
    }
  }

  val storage : DocumentController.Storage
}

object DocumentController extends DocumentController {
  object DatabaseStorage extends DocumentController.Storage {
    override def find(id: Long) = {
      DocumentFinder.byId(id).headOption.map(OverviewDocument.apply)
    }
  }

  override val storage = DatabaseStorage
}
