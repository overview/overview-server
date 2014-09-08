package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.{Document,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents}
import org.overviewproject.searchindex.IndexClient

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(documentSetId: Long, q: String): Future[Seq[DocumentInfo]]

  /** Lists all Document IDs for the given parameters. */
  def indexIds(documentSetId: Long, q: String): Future[Seq[Long]]

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

  override def indexIds(documentSetId: Long, q: String) = {
    import scala.concurrent.ExecutionContext.Implicits._

    if (q.isEmpty) {
      db { session => DbDocumentBackend.idsByDocumentSetId(documentSetId)(session) }
    } else {
      indexClient.searchForIds(documentSetId, q)
        .flatMap { (ids: Seq[Long]) =>
          if (ids.isEmpty) {
            Future.successful(Seq[Long]())
          } else {
            db { session => DbDocumentBackend.idsByIds(ids)(session) }
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
  import scala.language.implicitConversions

  private implicit class AugmentedDcoumentInfosQuery(query: Query[DocumentInfosImpl,DocumentInfosImpl#TableElementType]) {
    implicit def onlyIds(ids: Seq[Long]) = query.where(_.id inSet ids)
    implicit def sortedByInfo = query.sortBy(d => (d.title, d.suppliedId, d.pageNumber, d.id))
  }

  def _byDocumentSetIdCompiled(documentSetId: Column[Long]) = {
    DocumentInfos
      .where(_.documentSetId === documentSetId)
      .sortedByInfo
  }

  lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    _byDocumentSetIdCompiled(documentSetId)
  }

  lazy val idsByDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    _byDocumentSetIdCompiled(documentSetId)
      .map(_.id)
  }

  lazy val byIdCompiled = Compiled { (documentSetId: Column[Long], documentId: Column[Long]) =>
    Documents
      .where(_.documentSetId === documentSetId)
      .where(_.id === documentId)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list()(session)
  }

  def idsByDocumentSetId(documentSetId: Long)(session: Session) = {
    idsByDocumentSetIdCompiled(documentSetId).list()(session)
  }

  def byId(documentSetId: Long, documentId: Long)(session: Session) = {
    byIdCompiled(documentSetId, documentId).firstOption()(session)
  }

  private def _byIds(ids: Seq[Long]) = {
    DocumentInfos
      .onlyIds(ids)
      .sortedByInfo
  }

  def byIds(ids: Seq[Long])(session: Session) = {
    _byIds(ids)
      .list()(session)
  }

  def idsByIds(ids: Seq[Long])(session: Session) = {
    _byIds(ids)
      .map(_.id)
      .list()(session)
  }
}

object DocumentBackend extends DbDocumentBackend with DbBackend {
  override val indexClient = org.overviewproject.searchindex.TransportIndexClient.singleton
}
