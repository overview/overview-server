package controllers

import play.api.mvc.Controller

import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.finders.FinderResult.finderResultToIterable

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import models.orm.User
import models.orm.finders.DocumentSetFinder

trait SharedDocumentSetController extends Controller {
  trait Storage {
    def findDocumentSets(userEmail: String) : Iterable[(DocumentSet,User)]
  }

  def index = AuthorizedAction(anyUser) { implicit request =>
    val sharedDocumentSets = storage.findDocumentSets(request.user.email).toSeq

    Ok(views.html.SharedDocumentSet.index(request.user, sharedDocumentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  val storage : Storage
}

object SharedDocumentSetController extends SharedDocumentSetController {
  object DatabaseStorage extends SharedDocumentSetController.Storage {
    override def findDocumentSets(userEmail: String) = {
      DocumentSetFinder.byViewer(userEmail).withOwners
    }
  }

  override val storage = DatabaseStorage
}
