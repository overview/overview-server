package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.slick.jdbc.{GetResult,StaticQuery}

import org.overviewproject.models.{Document,DocumentHeader,DocumentInfo}
import org.overviewproject.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentsImpl,DocumentTags,DocumentStoreObjects,NodeDocuments,Tags}
import org.overviewproject.searchindex.IndexClient
import org.overviewproject.util.Logger

import models.pagination.{Page,PageInfo,PageRequest}
import models.{Selection,SelectionRequest}

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(
    selection: Selection,
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
  protected lazy val logger = Logger.forClass(getClass)

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

  private val UniversalIdSet: Set[Long] = new Set[Long] {
    override def contains(key: Long) = true
    override def iterator = throw new UnsupportedOperationException()
    override def +(elem: Long) = this
    override def -(elem: Long) = throw new UnsupportedOperationException()
  }

  protected val indexClient: IndexClient

  override def index(selection: Selection, pageRequest: PageRequest, includeText: Boolean) = {
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
  private def indexAllIds(documentSetId: Long): Future[Seq[Long]] = {
    logger.logExecutionTimeAsync("fetching sorted document IDs [docset {}]", documentSetId) {
      db { session =>
        DbDocumentBackend.sortedIds(documentSetId)
          .firstOption(session)
          .getOrElse(Seq())
      }
    }
  }

  /** Returns IDs that match a given search phrase, unsorted. */
  private def indexByQ(documentSetId: Long, q: String): Future[Set[Long]] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", q) {
      indexClient.searchForIds(documentSetId, q)
        .transform(_.toSet, exceptions.wrapElasticSearchException(_))
    }
  }

  /** Returns IDs that match the given tags/objects/etc (everything but
    * search pharse), unsorted.
    */
  private def indexByDB(request: SelectionRequest): Future[Set[Long]] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", request.toString) {
      list(DbDocumentBackend.idsBySelectionRequest(request))
        .map(_.toSet)
    }
  }

  /** Returns a subset of the DocumentSet's Document IDs, sorted. */
  private def indexSelectedIds(request: SelectionRequest): Future[Seq[Long]] = {
    val idsByQFuture: Future[Set[Long]] = if (request.q.isEmpty) {
      Future.successful(UniversalIdSet)
    } else {
      indexByQ(request.documentSetId, request.q)
    }

    val idsByDBFuture: Future[Set[Long]] = if (request.copy(q="").isAll) {
      Future.successful(UniversalIdSet)
    } else {
      indexByDB(request)
    }

    for {
      allSortedIds <- indexAllIds(request.documentSetId)
      idsByQ <- idsByQFuture
      idsByDB <- idsByDBFuture
    } yield {
      logger.logExecutionTime("filtering sorted document IDs [docset {}]", request.documentSetId) {
        allSortedIds
          .filter(idsByQ.contains(_))
          .filter(idsByDB.contains(_))
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

  def idsBySelectionRequest(request: SelectionRequest): Query[_,Long,Seq] = {
    var sql = DocumentInfos
      .filter(_.documentSetId === request.documentSetId)
      .map(_.id)

    if (request.documentIds.nonEmpty) {
      sql = sql.filter(_ inSet request.documentIds)
    }

    if (request.tagIds.nonEmpty) {
      val tagDocumentIds = DocumentTags
        .filter(_.tagId inSet request.tagIds)
        .map(_.documentId)
      sql = sql.filter(_ in tagDocumentIds)
    }

    if (request.nodeIds.nonEmpty) {
      val nodeDocumentIds = NodeDocuments
        .filter(_.nodeId inSet request.nodeIds)
        .map(_.documentId)
      sql = sql.filter(_ in nodeDocumentIds)
    }

    if (request.storeObjectIds.nonEmpty) {
      val storeObjectDocumentIds = DocumentStoreObjects
        .filter(_.storeObjectId inSet request.storeObjectIds)
        .map(_.documentId)
      sql = sql.filter(_ in storeObjectDocumentIds)
    }

    request.tagged.foreach { tagged =>
      val tagIds = Tags
        .filter(_.documentSetId === request.documentSetId)
        .map(_.id)

      val taggedDocumentIds = DocumentTags
        .filter(_.tagId in tagIds)
        .map(_.documentId)

      if (tagged) {
        sql = sql.filter(_ in taggedDocumentIds)
      } else {
        sql = sql.filter((id) => !(id in taggedDocumentIds))
      }
    }

    sql
  }

  object InfosByIds {
    private def q(ids: Seq[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Seq[Long]) = q(ids).map(_.id)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      DocumentInfos
        .filter(_.id inSet ids) // bind: we know we don't have 10M IDs here
    }
  }

  object DocumentsByIds {
    private def q(ids: Seq[Long]) = Documents.filter(_.id inSet ids)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      Documents
        .filter(_.id inSet ids) // bind: we know we don't have 10M IDs here
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
