package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.AuthorizedAction
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSet,DocumentSetUser}
import com.overviewdocs.models.tables.{DocumentSetUsers,DocumentSets}
import controllers.backend.DocumentSetBackend

trait SharedDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- storage.findDocumentSets(request.user.email)
    } yield Ok(views.html.SharedDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val documentSetBackend: DocumentSetBackend
  protected val storage: SharedDocumentSetController.Storage
}

object SharedDocumentSetController extends SharedDocumentSetController {
  trait Storage {
    def findDocumentSets(userEmail: String): Future[Seq[(DocumentSet,String)]]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    override def findDocumentSets(userEmail: String) = database.seq {
      DocumentSetUsers
        .filter(dsu => dsu.userEmail === userEmail && dsu.role === DocumentSetUser.Role(false))
        .join(DocumentSets).on { case (dsu, ds) => dsu.documentSetId === ds.id && !ds.deleted }
        .map { case (dsu, ds) => (ds, dsu.userEmail) }
    }
  }

  override protected val documentSetBackend = DocumentSetBackend
  override protected val storage = DatabaseStorage
}
