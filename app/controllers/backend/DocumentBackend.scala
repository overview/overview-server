package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.{Document,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,Documents}
import org.overviewproject.searchindex.IndexClient

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(documentSetId: Long, q: String): Future[Seq[DocumentInfo]]

  /** Returns a single Document. */
  def show(documentSetId: Long, document: Long): Future[Option[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  val indexClient: IndexClient

  override def index(documentSetId: Long, q: String) = {
    import scala.concurrent.ExecutionContext.Implicits._

    if (q.isEmpty) {
      db { session => DbDocumentBackend.byDocumentSetId(documentSetId)(session) }
    } else {
      indexClient.searchForIds(documentSetId, q)
        .flatMap { (ids: Seq[Long]) =>
          if (ids.isEmpty) {
            Future.successful(Seq[DocumentInfo]())
          } else {
            db { session => DbDocumentBackend.byIds(ids)(session) }
          }
        }
    }
  }

  override def show(documentSetId: Long, documentId: Long) = db { session =>
    DbDocumentBackend.byId(documentSetId, documentId: Long)(session)
  }
}

object DbDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    DocumentInfos
      .where(_.documentSetId === documentSetId)
      .sortBy(d => (d.title, d.suppliedId, d.pageNumber, d.id))
  }

  lazy val byIdCompiled = Compiled { (documentSetId: Column[Long], documentId: Column[Long]) =>
    Documents
      .where(_.documentSetId === documentSetId)
      .where(_.id === documentId)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list()(session)
  }

  def byId(documentSetId: Long, documentId: Long)(session: Session) = {
    byIdCompiled(documentSetId, documentId).firstOption()(session)
  }

  def byIds(ids: Seq[Long])(session: Session) = {
    DocumentInfos
      .where(_.id inSet ids) // Can't comiple inSet queries...
      .sortBy(d => (d.title, d.suppliedId, d.pageNumber, d.id))
      .list()(session)
  }
}

object DocumentBackend extends DbDocumentBackend with DbBackend {
  override val indexClient = org.overviewproject.searchindex.NodeIndexClient.singleton
}
