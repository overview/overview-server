package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.{Document,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentTags,NodeDocuments}
import org.overviewproject.searchindex.IndexClient

import models.pagination.{Page,PageInfo,PageRequest}
import models.{Selection,SelectionRequest}

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(selection: Selection, pageRequest: PageRequest): Future[Page[DocumentInfo]]

  /** Lists all Document IDs for the given parameters. */
  def indexIds(selectionRequest: SelectionRequest): Future[Seq[Long]]

  /** Returns a single Document. */
  def show(documentSetId: Long, documentId: Long): Future[Option[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  val indexClient: IndexClient

  override def index(selection: Selection, pageRequest: PageRequest) = {
    import scala.concurrent.ExecutionContext.Implicits._

    val total = selection.documentIds.length

    if (total == 0) {
      emptyPage[DocumentInfo](pageRequest)
    } else {
      val offset = pageRequest.offset
      val limit = pageRequest.limit
      val ids = selection.documentIds.slice(offset, offset + limit)

      list(DbDocumentBackend.byIds.page(ids))
        .map(Page(_, PageInfo(pageRequest, total)))
    }
  }

  override def indexIds(request: SelectionRequest) = {
    import scala.concurrent.ExecutionContext.Implicits._
    DbDocumentBackend.bySelectionRequest(request, indexClient)
      .flatMap(list(_))
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

  def bySelectionRequest(request: SelectionRequest, indexClient: IndexClient) = {
    import scala.concurrent.ExecutionContext.Implicits._

    var sql = DocumentInfos
      .filter(_.documentSetId === request.documentSetId)

    if (request.tagIds.nonEmpty) {
      val tagDocumentIds = DocumentTags
        .filter(_.tagId inSet request.tagIds)
        .map(_.documentId)
      sql = sql.filter(_.id in tagDocumentIds)
    }

    if (request.nodeIds.nonEmpty) {
      val nodeDocumentIds = NodeDocuments
        .filter(_.nodeId inSet request.nodeIds)
        .map(_.documentId)
      sql = sql.filter(_.id in nodeDocumentIds)
    }

    val futureSql = request.q match {
      case "" => Future.successful(sql)
      case s => {
        indexClient.searchForIds(request.documentSetId, s)
          .map { (ids: Seq[Long]) => sql.filter(_.id inSet ids) }
      }
    }

    futureSql.map(_.sortBy(sortKey).map(_.id))
  }

  object byIds {
    private def q(ids: Seq[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Seq[Long]) = q(ids).sortedByInfo.map(_.id)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      // We use inSetBind instead of inSet, because we know there's a maximum
      // number of document IDs. (This request is called within a page.)
      DocumentInfos
        .filter(_.id inSetBind ids) // bind: we know we don't have 10M IDs here
        .sortedByInfo // this is O(1) because we have a maximum number of IDs
    }
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
