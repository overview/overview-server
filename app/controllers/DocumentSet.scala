package controllers

import play.api.mvc.{Action,Controller}

object DocumentSet extends Controller {
    def index(documentSetId: Long) = Action {
        Ok(views.html.DocumentSet.index())
    }
}
