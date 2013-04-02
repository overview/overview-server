package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.{OverviewDocumentSet,OverviewUser}
import models.orm.finders.DocumentSetFinder
import models.orm.{DocumentSet,User}

trait PublicDocumentSetController extends Controller {
  trait Storage {
    def findDocumentSets : Iterable[(OverviewDocumentSet,OverviewUser)]
  }

  def index = AuthorizedAction(anyUser) { implicit request =>
    val documentSets = storage.findDocumentSets.toSeq

    Ok(views.html.PublicDocumentSet.index(documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  val storage : Storage
}

object PublicDocumentSetController extends PublicDocumentSetController {
  object DatabaseStorage extends PublicDocumentSetController.Storage {
    override def findDocumentSets = {
      DocumentSetFinder.byIsPublic(true).withOwners.map { tuple: (DocumentSet,User) =>
        (OverviewDocumentSet(tuple._1), OverviewUser(tuple._2))
      }
    }
  }

  override val storage = DatabaseStorage
}
