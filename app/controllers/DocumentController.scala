package controllers

import java.sql.Connection
import models.DocumentLoader
import play.api.mvc.{Action,AnyContent, Request}
import play.api.db.DB
import play.api.Play.current

object DocumentController extends BaseController {
  def show(documentId: Long) =
    authorizedAction(userOwningDocument(documentId))(user =>
      this.authorizedShow(documentId)(_: Request[AnyContent], _: Connection))

  def authorizedShow(documentId: Long)(implicit request: Request[AnyContent], connection: Connection) = {
    val documentLoader = new DocumentLoader()
    val document = documentLoader.load(documentId)
    document match {
      case Some(d) => Ok(views.html.Document.show(d))
      case None => NotFound
    }
  }
}
