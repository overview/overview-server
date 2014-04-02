package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser

trait CsvUploadController extends Controller {
  def new_() = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.CsvUpload.new_(request.user))
  }
}

object CsvUploadController extends CsvUploadController
