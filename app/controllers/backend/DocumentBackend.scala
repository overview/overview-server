package controllers.backend

import scala.concurrent.Future

import org.overviewproject.searchindex.IndexClient
import org.overviewproject.tree.orm.Document // FIXME should be models
import org.overviewproject.models.tables.Documents

trait DocumentBackend {
  /** Lists all Documents for the given parameters.
    */
  def index(documentSetId: Long, q: String): Future[Seq[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  val indexClient: IndexClient

  override def index(documentSetId: Long, q: String) = {
    import scala.concurrent.ExecutionContext.Implicits._

    indexClient.searchForIds(documentSetId, if (q.isEmpty) "*:*" else q)
      .flatMap { (ids: Seq[Long]) =>
        if (ids.isEmpty) {
          Future.successful(Seq[Document]())
        } else {
          db { session =>
            DbDocumentBackend.byIds(ids)(session)
          }
        }
      }
  }
}

object DbDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  def byIds(ids: Seq[Long])(session: Session) = {
    Documents.where(_.id inSet ids)
      .sortBy(d => (d.title, d.pageNumber, d.description, d.id))
      .list()(session)
  }
}

object DocumentBackend extends DbDocumentBackend with DbBackend {
  override val indexClient = org.overviewproject.searchindex.NodeIndexClient.singleton
}
