package controllers

import models.DocumentLoader
import play.api.mvc.{Action,Controller}
import play.api.db.DB
import play.api.Play.current

object DocumentController extends Controller {
    def show(documentId: Long) = Action {
      DB.withTransaction { implicit connection =>
      	val documentLoader = new DocumentLoader()
      	val document = documentLoader.load(documentId)
      	Ok(views.html.Document.show(document.get))
      }
    }
}
