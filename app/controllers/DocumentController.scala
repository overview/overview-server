package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocument
import models.OverviewDocument

trait DocumentController extends Controller {
  def findDocumentById(documentId: Long): Option[OverviewDocument]

  def show(documentId: Long) = AuthorizedAction(userOwningDocument(documentId)) { implicit request =>
    findDocumentById(documentId) match {
      case Some(document) => Ok(views.html.Document.show(document))
      case None => NotFound
    }
  }
}

object DocumentController extends DocumentController {
  override def findDocumentById(documentId: Long) = OverviewDocument.findById(documentId)
}
