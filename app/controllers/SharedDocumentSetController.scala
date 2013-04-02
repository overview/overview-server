package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.orm.finders.DocumentSetFinder
import models.orm.{DocumentSet,User}
import models.{OverviewDocumentSet,OverviewUser}

trait SharedDocumentSetController extends Controller {
  trait Storage {
    def findDocumentSets(userEmail: String) : Iterable[(OverviewDocumentSet,OverviewUser)]
  }

  def index = AuthorizedAction(anyUser) { implicit request =>
    val sharedDocumentSets = storage.findDocumentSets(request.user.email).toSeq

    Ok(views.html.SharedDocumentSet.index(sharedDocumentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  val storage : Storage
}

object SharedDocumentSetController extends SharedDocumentSetController {
  object DatabaseStorage extends SharedDocumentSetController.Storage {
    override def findDocumentSets(userEmail: String) = {
      DocumentSetFinder.byViewer(userEmail).withOwners.map { tuple: (DocumentSet,User) =>
        (OverviewDocumentSet(tuple._1), OverviewUser(tuple._2))
      }
    }
  }

  override val storage = DatabaseStorage
}
