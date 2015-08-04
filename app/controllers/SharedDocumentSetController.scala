package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSet,DocumentSetUser}
import com.overviewdocs.models.tables.{DocumentSetUsers,DocumentSets}

trait SharedDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- storage.countUserOwnedDocumentSets(request.user.email)
      documentSets <- storage.findDocumentSets(request.user.email)
    } yield Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val storage: SharedDocumentSetController.Storage
}

object SharedDocumentSetController extends SharedDocumentSetController {
  trait Storage {
    def findDocumentSets(userEmail: String): Future[Seq[(DocumentSet,String)]]
    def countUserOwnedDocumentSets(user: String): Future[Int]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    override def findDocumentSets(userEmail: String) = database.seq {
      DocumentSetUsers
        .filter(dsu => dsu.userEmail === userEmail && dsu.role === DocumentSetUser.Role(false))
        .join(DocumentSets).on(_.documentSetId === _.id)
        .map { case (dsu, ds) => (ds, dsu.userEmail) }
    }

    override def countUserOwnedDocumentSets(owner: String) = database.length {
      DocumentSetUsers.filter(dsu => dsu.userEmail === owner && dsu.role === DocumentSetUser.Role(true))
    }
  }

  override protected val storage = DatabaseStorage
}
