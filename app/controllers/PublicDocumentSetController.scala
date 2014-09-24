package controllers

import org.overviewproject.tree.orm.DocumentSet
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.orm.finders.DocumentSetFinder
import models.User

trait PublicDocumentSetController extends Controller {
  trait Storage {
    def findDocumentSets : Iterable[(DocumentSet,User)]
    def countUserOwnedDocumentSets(user: String) : Long
  }

  def index = AuthorizedAction.inTransaction(anyUser) { implicit request =>
    val documentSets = storage.findDocumentSets
    val count = storage.countUserOwnedDocumentSets(request.user.email)

    Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  val storage : Storage
}

object PublicDocumentSetController extends PublicDocumentSetController {
  object DatabaseStorage extends PublicDocumentSetController.Storage {
    override def findDocumentSets = {
      DocumentSetFinder.byIsPublic(true).withOwners
    }

    override def countUserOwnedDocumentSets(owner: String) = {
      DocumentSetFinder.byOwner(owner).count
    }
  }

  override val storage = DatabaseStorage
}
