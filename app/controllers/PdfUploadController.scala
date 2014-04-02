package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser

trait PdfUploadController extends Controller {
  def new_() = AuthorizedAction(anyUser) { implicit request =>
    Ok(views.html.PdfUpload.new_(request.user))
  }
}

object PdfUploadController extends PdfUploadController
