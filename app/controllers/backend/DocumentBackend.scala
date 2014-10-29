package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.{Document,DocumentHeader,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentsImpl,DocumentSearchResults,DocumentTags,DocumentStoreObjects,NodeDocuments,Tags}
import org.overviewproject.searchindex.IndexClient

import models.pagination.{Page,PageInfo,PageRequest}
import models.{SelectionLike,SelectionRequest}

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(
    selection: SelectionLike,
    pageRequest: PageRequest,
    includeText: Boolean
  ): Future[Page[DocumentHeader]]

  /** Lists all Document IDs for the given parameters. */
  def indexIds(selectionRequest: SelectionRequest): Future[Seq[Long]]

  /** Returns a single Document. */
  def show(documentSetId: Long, documentId: Long): Future[Option[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  val indexClient: IndexClient

  override def index(selection: SelectionLike, pageRequest: PageRequest, includeText: Boolean) = {
    import scala.concurrent.ExecutionContext.Implicits._

    selection.getDocumentIds(pageRequest)
      .flatMap { (page: Page[Long]) =>
        if (page.pageInfo.total == 0) {
          emptyPage[DocumentHeader](pageRequest)
        } else {
          val items: Future[Seq[DocumentHeader]] = includeText match {
            case false => list(DbDocumentBackend.InfosByIds.page(page.items))
            case true => list(DbDocumentBackend.DocumentsByIds.page(page.items))
          }
          items.map(Page(_, page.pageInfo))
        }
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
  private def sortKey(document: DocumentsImpl) = (document.title, document.suppliedId, document.pageNumber, document.id)

  private implicit class AugmentedDocumentInfosQuery(query: Query[DocumentInfosImpl,DocumentInfosImpl#TableElementType,Seq]) {
    implicit def sortedByInfo = query.sortBy(sortKey)
  }
  private implicit class AugmentedDocumentsQuery(query: Query[DocumentsImpl,DocumentsImpl#TableElementType,Seq]) {
    implicit def sortedByInfo = query.sortBy(sortKey)
  }

  def bySelectionRequest(request: SelectionRequest, indexClient: IndexClient) = {
    import scala.concurrent.ExecutionContext.Implicits._

    var sql = DocumentInfos
      .filter(_.documentSetId === request.documentSetId)

    if (request.documentIds.nonEmpty) {
      sql = sql.filter(_.id inSet request.documentIds)
    }

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

    if (request.searchResultIds.nonEmpty) {
      val searchResultDocumentIds = DocumentSearchResults
        .filter(_.searchResultId inSet request.searchResultIds)
        .map(_.documentId)
      sql = sql.filter(_.id in searchResultDocumentIds)
    }

    if (request.storeObjectIds.nonEmpty) {
      val storeObjectDocumentIds = DocumentStoreObjects
        .filter(_.storeObjectId inSet request.storeObjectIds)
        .map(_.documentId)
      sql = sql.filter(_.id in storeObjectDocumentIds)
    }

    request.tagged.foreach { tagged =>
      val tagIds = Tags
        .filter(_.documentSetId === request.documentSetId)
        .map(_.id)

      val taggedDocumentIds = DocumentTags
        .filter(_.tagId in tagIds)
        .map(_.documentId)

      if (tagged) {
        sql = sql.filter(_.id in taggedDocumentIds)
      } else {
        sql = sql.filter((d) => !(d.id in taggedDocumentIds))
      }
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

  object InfosByIds {
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

  object DocumentsByIds {
    private def q(ids: Seq[Long]) = Documents.filter(_.id inSet ids)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      // We use inSetBind instead of inSet, because we know there's a maximum
      // number of document IDs. (This request is called within a page.)
      Documents
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
