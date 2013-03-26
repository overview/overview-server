package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.OverviewDocumentSet

trait SharedDocumentSetController extends Controller {

  def index = AuthorizedAction(anyUser) { implicit request =>
    val sharedDocumentSets = OverviewDocumentSet.findByViewer(request.user.email)
    Ok(views.html.SharedDocumentSet.index(sharedDocumentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }
}

object SharedDocumentSetController extends SharedDocumentSetController
