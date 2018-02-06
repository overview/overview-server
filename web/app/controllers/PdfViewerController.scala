package controllers

import javax.inject.Inject

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction

class PdfViewerController @Inject() (
  val controllerComponents: ControllerComponents,
  pdfViewerHtml: views.html.PdfViewer.show
) extends BaseController {
  def show = authorizedAction(anyUser) { implicit request =>
    Ok(pdfViewerHtml())
  }
}
