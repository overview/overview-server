package controllers

import models.Document

import play.api.data._
import play.api.mvc.{Action,Controller}

object DocumentController extends Controller {
    def show(documentId: Long) = Action {
        val document = Document.find.byId(documentId) // TODO: check user access
        Ok(views.html.Document.show(document))
    }
}
