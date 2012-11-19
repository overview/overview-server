package controllers

import java.sql.Connection
import play.api.mvc.{AnyContent,Request}

import models.OverviewDocument

object DocumentController extends BaseController {
  def show(documentId: Long) =
    authorizedAction(userOwningDocument(documentId))(user =>
      this.authorizedShow(documentId)(_: Request[AnyContent], _: Connection))

  def authorizedShow(documentId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    OverviewDocument.findById(documentId) match {
      case Some(document) => Ok(views.html.Document.show(document))
      case None => NotFound
    }
  }
}
