package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.{Document,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents}
import org.overviewproject.searchindex.IndexClient

import models.pagination.{Page,PageRequest}

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(documentSetId: Long, q: String, pageRequest: PageRequest): Future[Page[DocumentInfo]]

  /** Lists all Document IDs for the given parameters. */
  def indexIds(documentSetId: Long, q: String): Future[Seq[Long]]

  /** Returns a single Document. */
  def show(documentSetId: Long, document: Long): Future[Option[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  val indexClient: IndexClient

  override def index(documentSetId: Long, q: String, pageRequest: PageRequest) = {
    import scala.concurrent.ExecutionContext.Implicits._

    if (q.isEmpty) {
      val o = DbDocumentBackend.byDocumentSetId
      val itemsQ = o.page(documentSetId, pageRequest.offset, pageRequest.limit)
      val countQ = o.count(documentSetId)
      page(itemsQ, countQ, pageRequest)
    } else {
      indexClient.searchForIds(documentSetId, q)
        .flatMap { (ids: Seq[Long]) =>
          if (ids.isEmpty) {
            emptyPage[DocumentInfo](pageRequest)
          } else {
            val o = DbDocumentBackend.byIds
            val itemsQ = o.page(ids, pageRequest.offset, pageRequest.limit)
            val countQ = o.count(ids)
            page(itemsQ, countQ, pageRequest)
          }
        }
    }
  }

  override def indexIds(documentSetId: Long, q: String) = {
    import scala.concurrent.ExecutionContext.Implicits._

    val pageRequest = PageRequest(0, 10000000)

    if (q.isEmpty) {
      list(DbDocumentBackend.byDocumentSetId.ids(documentSetId))
    } else {
      indexClient.searchForIds(documentSetId, q)
        .flatMap { (ids: Seq[Long]) =>
          if (ids.isEmpty) {
            Future.successful(Seq[Long]())
          } else {
            list(DbDocumentBackend.byIds.ids(ids))
          }
        }
    }
  }

  override def show(documentSetId: Long, documentId: Long) = {
    firstOption(DbDocumentBackend.byId(documentSetId, documentId))
  }
}

object DbDocumentBackend {
  import org.overviewproject.database.Slick.simple._
  import scala.language.implicitConversions

  private def sortKey(info: DocumentInfosImpl) = (info.title, info.suppliedId, info.pageNumber, info.id)

  private implicit class AugmentedDocumentInfosQuery(query: Query[DocumentInfosImpl,DocumentInfosImpl#TableElementType,Seq]) {
    implicit def sortedByInfo = query.sortBy(sortKey)
  }

  object byDocumentSetId {
    private def q(documentSetId: Column[Long]) = DocumentInfos.filter(_.documentSetId === documentSetId)

    lazy val ids = Compiled { (documentSetId: Column[Long]) => q(documentSetId).sortedByInfo.map(_.id) }

    lazy val page = Compiled { (documentSetId: Column[Long], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      q(documentSetId)
        .sortedByInfo
        .drop(offset)
        .take(limit)
    }

    lazy val count = Compiled { (documentSetId: Column[Long]) => q(documentSetId).length }
  }

  object byIds {
    private def q(ids: Seq[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Seq[Long]) = q(ids).sortedByInfo.map(_.id)

    def page(ids: Seq[Long], offset: Int, limit: Int) = {
      q(ids)
        .sortedByInfo
        .drop(offset)
        .take(limit)
    }

    def count(ids: Seq[Long]) = q(ids).length
  }

  lazy val byId = Compiled { (documentSetId: Column[Long], documentId: Column[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
  }
}

object DocumentBackend extends DbDocumentBackend with DbBackend {
  override val indexClient = org.overviewproject.searchindex.TransportIndexClient.singleton
}
