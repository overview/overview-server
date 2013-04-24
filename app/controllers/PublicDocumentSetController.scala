package controllers

import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.orm.finders.DocumentSetFinder
import models.orm.{DocumentSet,User}

trait PublicDocumentSetController extends Controller {
  trait Storage {
    def findDocumentSets : Iterable[(DocumentSet,User)]
  }

  def index = AuthorizedAction(anyUser) { implicit request =>
    val documentSets = storage.findDocumentSets

    Ok(views.html.PublicDocumentSet.index(documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  val storage : Storage
}

object PublicDocumentSetController extends PublicDocumentSetController {
  object DatabaseStorage extends PublicDocumentSetController.Storage {
    override def findDocumentSets = {
      DocumentSetFinder.byIsPublic(true).withOwners
    }
  }

  override val storage = DatabaseStorage
}
