package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSet,DocumentSetUser}
import com.overviewdocs.models.tables.{DocumentSets,DocumentSetUsers}
import controllers.backend.DocumentSetBackend

trait PublicDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
      documentSets <- storage.findDocumentSets
    } yield Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val documentSetBackend: DocumentSetBackend
  protected val storage: PublicDocumentSetController.Storage
}

object PublicDocumentSetController extends PublicDocumentSetController {
  trait Storage {
    def findDocumentSets: Future[Seq[(DocumentSet,String)]]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    override def findDocumentSets = database.seq {
      DocumentSetUsers
        .filter(_.role === DocumentSetUser.Role(true))
        .join(DocumentSets).on { case (dsu, ds) => dsu.documentSetId === ds.id && ds.isPublic && !ds.deleted }
        .map { case (dsu, ds) => (ds, dsu.userEmail) }
    }
  }

  override protected val documentSetBackend = DocumentSetBackend
  override protected val storage = DatabaseStorage
}
