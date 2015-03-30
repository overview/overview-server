package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.slick.jdbc.{GetResult,StaticQuery}

import org.overviewproject.models.{Document,DocumentHeader,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentsImpl,DocumentTags,DocumentStoreObjects,NodeDocuments,Tags}
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

  /** Lists all Document IDs for the given parameters.
    *
    * May fail with exceptions.SearchParseFailed if selectionRequest contains
    * `q` and ElasticSearch can't parse the query.
    */
  def indexIds(selectionRequest: SelectionRequest): Future[Seq[Long]]

  /** Returns a single Document. */
  def show(documentSetId: Long, documentId: Long): Future[Option[Document]]

  def show(documentId: Long): Future[Option[Document]]
}

trait DbDocumentBackend extends DocumentBackend { self: DbBackend =>
  private val NullDocumentHeader = new DocumentHeader {
    override val id = 0L
    override val documentSetId = 0L
    override val url = None
    override val suppliedId = ""
    override val title = ""
    override val pageNumber = None
    override val keywords = Seq()
    override val createdAt = new java.util.Date(0L)
    override val text = ""
  }

  protected val indexClient: IndexClient

  override def index(selection: SelectionLike, pageRequest: PageRequest, includeText: Boolean) = {
    selection.getDocumentIds(pageRequest)
      .flatMap { (page: Page[Long]) =>
        if (page.pageInfo.total == 0) {
          emptyPage[DocumentHeader](pageRequest)
        } else {
          val documentsFuture: Future[Seq[DocumentHeader]] = includeText match {
            case false => list(DbDocumentBackend.InfosByIds.page(page.items))
            case true => list(DbDocumentBackend.DocumentsByIds.page(page.items))
          }

          documentsFuture.map { documents: Seq[DocumentHeader] =>
            val documentsById: Map[Long,DocumentHeader] = documents
              .map(document => (document.id -> document))
              .toMap

            page.map(id => documentsById.getOrElse(id, NullDocumentHeader))
          }
        }
      }
  }

  /** Returns all a DocumentSet's Document IDs, sorted. */
  private def indexAllIds(documentSetId: Long): Future[Seq[Long]] = db { session =>
    DbDocumentBackend.sortedIds(documentSetId)
      .firstOption(session)
      .getOrElse(Seq())
  }

  /** Returns a subset of the DocumentSet's Document IDs, sorted. */
  private def indexSelectedIds(request: SelectionRequest): Future[Seq[Long]] = {
    val selectedUnsortedIdsFuture: Future[Seq[Long]] =
      DbDocumentBackend.bySelectionRequest(request, indexClient).flatMap(list(_))

    for {
      allSortedIds <- indexAllIds(request.documentSetId)
      selectedIds <- selectedUnsortedIdsFuture
    } yield {
      if (allSortedIds.length == selectedIds.length) {
        // Fast path: selectedIds contains the same elements as allSortedIds.
        //
        // This happens when, say, we select all documents in the root Node of
        // an up-to-date Tree.
        allSortedIds
      } else {
        // Catch-all: selectedIds is a subset of allSortedIds.
        val set = selectedIds.toSet
        allSortedIds.filter(set.contains(_))
      }
    }
  }

  override def indexIds(request: SelectionRequest) = {
    if (request.isAll) {
      indexAllIds(request.documentSetId)
    } else {
      indexSelectedIds(request)
    }
  }

  override def show(documentSetId: Long, documentId: Long) = {
    firstOption(DbDocumentBackend.byDocumentSetIdAndId(documentSetId, documentId))
  }

  override def show(documentId: Long) = {
    firstOption(DbDocumentBackend.byId(documentId))
  }
}

object DbDocumentBackend {
  import org.overviewproject.database.Slick.simple._
  import scala.language.implicitConversions

  def sortedIds(documentSetId: Long) = {
    implicit val rconv: GetResult[Seq[Long]] = GetResult(r => (r.nextLongArray()))

    // The ORM is unaware of DocumentSet.sortedDocumentIds
    val q = "SELECT sorted_document_ids FROM document_set WHERE id = ?"
    val sq = StaticQuery.query[Long,Seq[Long]](q)
    sq.apply(documentSetId)
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

    request.q match {
      case "" => Future.successful(sql.map(_.id))
      case s => {
        indexClient.searchForIds(request.documentSetId, s)
          .transform(identity(_), exceptions.wrapElasticSearchException(_))
          .map { (ids: Seq[Long]) => sql.filter(_.id inSet ids).map(_.id) }
      }
    }
  }

  object InfosByIds {
    private def q(ids: Seq[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Seq[Long]) = q(ids).map(_.id)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      // We use inSetBind instead of inSet, because we know there's a maximum
      // number of document IDs. (This request is called within a page.)
      DocumentInfos
        .filter(_.id inSetBind ids) // bind: we know we don't have 10M IDs here
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
    }
  }

  lazy val byDocumentSetIdAndId = Compiled { (documentSetId: Column[Long], documentId: Column[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
  }

  lazy val byId = Compiled { (documentId: Column[Long]) =>
    Documents.filter(_.id === documentId)
  }
}

object DocumentBackend extends DbDocumentBackend with DbBackend {
  override protected val indexClient = org.overviewproject.searchindex.TransportIndexClient.singleton
}
