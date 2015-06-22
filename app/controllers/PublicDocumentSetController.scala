package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import org.overviewproject.database.HasDatabase
import org.overviewproject.models.{DocumentSet,DocumentSetUser}
import org.overviewproject.models.tables.{DocumentSets,DocumentSetUsers}

trait PublicDocumentSetController extends Controller {
  def index = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- storage.countUserOwnedDocumentSets(request.user.email)
      documentSets <- storage.findDocumentSets
    } yield Ok(views.html.PublicDocumentSet.index(request.user, count, documentSets))
      .withHeaders(CACHE_CONTROL -> "max-age=0")
  }

  protected val storage: PublicDocumentSetController.Storage
}

object PublicDocumentSetController extends PublicDocumentSetController {
  trait Storage {
    def findDocumentSets: Future[Seq[(DocumentSet,String)]]
    def countUserOwnedDocumentSets(user: String): Future[Int]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    import database.api._

    override def findDocumentSets = database.seq {
      DocumentSetUsers
        .filter(_.role === DocumentSetUser.Role(true))
        .join(DocumentSets).on { case (dsu, ds) => dsu.documentSetId === ds.id && ds.isPublic === true }
        .map { case (dsu, ds) => (ds, dsu.userEmail) }
    }

    override def countUserOwnedDocumentSets(owner: String) = database.length {
      DocumentSetUsers.filter(dsu => dsu.userEmail === owner && dsu.role === DocumentSetUser.Role(true))
    }
  }

  override protected val storage = DatabaseStorage
}
