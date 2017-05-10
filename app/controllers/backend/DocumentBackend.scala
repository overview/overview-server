package controllers.backend

import play.api.libs.json.JsObject
import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import com.overviewdocs.models.{Document,DocumentDisplayMethod,DocumentHeader,PdfNoteCollection}
import com.overviewdocs.models.tables.{DocumentInfos,DocumentInfosImpl,Documents,DocumentsImpl,DocumentTags,Tags}
import com.overviewdocs.query.{Query=>SearchQuery}
import com.overviewdocs.searchindex.IndexClient
import com.overviewdocs.util.Logger
import models.pagination.{Page,PageRequest}
import models.{Selection,SelectionRequest}

trait DocumentBackend {
  /** Lists all Documents for the given parameters. */
  def index(
    selection: Selection,
    pageRequest: PageRequest,
    includeText: Boolean
  ): Future[Page[DocumentHeader]]

  /** Lists all requested Documents, in the requested order. */
  def index(documentSetId: Long, documentIds: Seq[Long]): Future[Seq[Document]]

  /** Lists all Document IDs for the given parameters.
    *
    * Will only fail is a server is down.
    */
  def indexIds(selectionRequest: SelectionRequest): Future[Seq[Long]]

  /** Returns a single Document.
    *
    * Will not find the document if it is in a different document set than the
    * one specified. (This is useful both for security and for scaling.)
    */
  def show(documentSetId: Long, documentId: Long): Future[Option[Document]]

  /** Returns a single Document.
    *
    * Avoid this method in favor of show(Long,Long). The extra parameter will
    * help us scale.
    */
  def show(documentId: Long): Future[Option[Document]]

  /** Updates a Document's title.
    *
    * Is a no-op if the Document does not exist or if it exists in a different
    * DocumentSet.
    */
  def updateTitle(documentSetId: Long, documentId: Long, title: String): Future[Unit]

  /** Updates a Document's metadata.
    *
    * Is a no-op if the Document does not exist or if it exists in a different
    * DocumentSet.
    */
  def updateMetadataJson(documentSetId: Long, documentId: Long, metadataJson: JsObject): Future[Unit]

  /** Updates a Document's pdfNotes.
   *
   * Is a no-op if the Document does not exist.
   */
  def updatePdfNotes(documentId: Long, pdfNotes: PdfNoteCollection): Future[Unit]
}

trait DbDocumentBackend extends DocumentBackend with DbBackend {
  import database.api._

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
    override val displayMethod = DocumentDisplayMethod.auto
    override val isFromOcr = false
    override val metadataJson = JsObject(Seq())
    override val text = ""
    override val pdfNotes = PdfNoteCollection(Array())
    override val thumbnailLocation = None
  }

  private val UniversalIdSet: Set[Long] = new Set[Long] {
    override def contains(key: Long) = true
    override def iterator = throw new UnsupportedOperationException()
    override def +(elem: Long) = this
    override def -(elem: Long) = throw new UnsupportedOperationException()
  }

  protected val indexClient: IndexClient

  override def index(selection: Selection, pageRequest: PageRequest, includeText: Boolean): Future[Page[DocumentHeader]] = {
    selection.getDocumentIds(pageRequest)
      .flatMap { (page: Page[Long]) =>
        if (page.pageInfo.total == 0) {
          emptyPage[DocumentHeader](pageRequest)
        } else {
          val documentsFuture: Future[Seq[DocumentHeader]] = includeText match {
            case false => database.seq(InfosByIds.page(page.items))
            case true => database.seq(DocumentsByIds.page(page.items))
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

  override def index(documentSetId: Long, documentIds: Seq[Long]) = {
    database.seq(byDocumentSetIdAndIds(documentSetId, documentIds)).map { documents =>
      val map: Map[Long,Document] = documents.map((d) => (d.id -> d)).toMap
      documentIds.collect(map)
    }
  }

  /** Returns all a DocumentSet's Document IDs, sorted. */
  private def indexAllIds(documentSetId: Long): Future[Seq[Long]] = {
    logger.logExecutionTimeAsync("fetching sorted document IDs [docset {}]", documentSetId) {
      database.option(sortedIds(documentSetId)).map(_.getOrElse(Seq()))
    }
  }

  /** Returns IDs that match a given search phrase, unsorted. */
  private def indexByQ(documentSetId: Long, q: SearchQuery): Future[Set[Long]] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", q.toString) {
      indexClient.searchForIds(documentSetId, q).map(_.toSet)
    }
  }

  /** Returns IDs that match the given tags/objects/etc (everything but
    * search pharse), unsorted.
    */
  private def indexByDB(request: SelectionRequest): Future[Set[Long]] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", request.toString) {
      implicit val getLong = slick.jdbc.GetResult(r => r.nextLong)
      database.run(sql"#${idsBySelectionRequestSql(request)}".as[Long]).map(_.toSet)
    }
  }

  /** Returns a subset of the DocumentSet's Document IDs, sorted. */
  private def indexSelectedIds(request: SelectionRequest): Future[Seq[Long]] = {
    val idsByQFuture = request.q match {
      case None => Future.successful(UniversalIdSet)
      case Some(q) => indexByQ(request.documentSetId, q)
    }

    val idsByDBFuture: Future[Set[Long]] = if (request.copy(q=None).isAll) {
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
    database.option(byDocumentSetIdAndId(documentSetId, documentId))
  }

  override def show(documentId: Long) = {
    database.option(byId(documentId))
  }

  override def updateTitle(documentSetId: Long, documentId: Long, title: String) = {
    for {
      _ <- database.runUnit(updateTitleCompiled(documentSetId, documentId).update(Some(title)))
      _ <- database.option(byDocumentSetIdAndId(documentSetId, documentId)).flatMap(_ match {
        case Some(document) => {
          for {
            _ <- indexClient.addDocuments(Seq(document.copy(title=title)))
            _ <- indexClient.refresh
          } yield ()
        }
        case _ => Future.successful(())
      })
    } yield ()
  }

  override def updateMetadataJson(documentSetId: Long, documentId: Long, metadataJson: JsObject) = {
    database.runUnit(updateMetadataJsonCompiled(documentSetId, documentId).update(Some(metadataJson)))
  }

  override def updatePdfNotes(documentId: Long, pdfNotes: PdfNoteCollection) = {
    database.runUnit(updatePdfNotesCompiled(documentId).update(Some(pdfNotes)))
  }

  protected def sortedIds(documentSetId: Long): DBIO[Seq[Seq[Long]]] = {
    // The ORM is unaware of DocumentSet.sortedDocumentIds
    sql"SELECT sorted_document_ids FROM document_set WHERE id = ${documentSetId}".as[Seq[Long]]
  }

  protected def idsBySelectionRequestSql(request: SelectionRequest): String = {
    // Don't have to worry about SQL injection: every SelectionRequest
    // parameter is an ID. (Or it's "q", which this method ignores.)
    val sb = new StringBuilder(s"""SELECT id FROM document WHERE document_set_id = ${request.documentSetId}""")

    if (request.documentIds.nonEmpty) {
      sb.append(s"""
        AND id IN (${request.documentIds.mkString(",")})""")
    }

    if (request.nodeIds.nonEmpty) {
      sb.append(s"""
        AND EXISTS (
          SELECT 1 FROM node_document WHERE document_id = document.id
          AND node_id IN (${request.nodeIds.mkString(",")})
        )""")
    }

    if (request.storeObjectIds.nonEmpty) {
      sb.append(s"""
        AND EXISTS (
          SELECT 1 FROM document_store_object WHERE document_id = document.id
          AND store_object_id IN (${request.storeObjectIds.mkString(",")})
        )""")
    }

    if (request.tagIds.nonEmpty || request.tagged.nonEmpty) {
      val taggedSql = "EXISTS (SELECT 1 FROM document_tag WHERE document_id = document.id)"

      request.tagOperation match {
        case SelectionRequest.TagOperation.Any => {
          val parts = Buffer[String]()

          if (request.tagIds.nonEmpty) {
            parts.append(s"""EXISTS (
              SELECT 1
              FROM document_tag
              WHERE document_id = document.id
                AND tag_id IN (${request.tagIds.mkString(",")})
            )""")
          }

          request.tagged match {
            case Some(true) => parts.append(taggedSql)
            case Some(false) => parts.append("NOT " + taggedSql)
            case None =>
          }

          sb.append(s" AND (${parts.mkString(" OR ")})")
        }

        case SelectionRequest.TagOperation.All => {
          for (tagId <- request.tagIds) {
            sb.append(s"""
              AND EXISTS (SELECT 1 FROM document_tag WHERE document_id = document.id AND tag_id = $tagId)""")
          }

          request.tagged match {
            case Some(true) => sb.append("\nAND " + taggedSql)
            case Some(false) => sb.append("\nAND NOT " + taggedSql)
            case None =>
          }
        }

        case SelectionRequest.TagOperation.None => {
          if (request.tagIds.nonEmpty) {
            sb.append(s"""
              AND NOT EXISTS (
                SELECT 1
                FROM document_tag
                WHERE document_id = document.id
                  AND tag_id IN (${request.tagIds.mkString(",")})
              )""")
          }

          request.tagged match {
            case Some(true) => sb.append("\nAND NOT " + taggedSql)
            case Some(false) => sb.append("\nAND " + taggedSql)
            case None =>
          }
        }
      }
    }

    sb.toString
  }

  protected object InfosByIds {
    private def q(ids: Seq[Long]) = DocumentInfos.filter(_.id inSet ids)

    def ids(ids: Seq[Long]) = q(ids).map(_.id)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      DocumentInfos.filter(_.id inSet ids) // we know we don't have 10M IDs here
    }
  }

  protected object DocumentsByIds {
    private def q(ids: Seq[Long]) = Documents.filter(_.id inSet ids)

    def page(ids: Seq[Long]) = {
      // We call this one when we're paginating.
      Documents
        .filter(_.id inSet ids) // bind: we know we don't have 10M IDs here
    }
  }

  private lazy val byDocumentSetIdAndId = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
  }

  private def byDocumentSetIdAndIds(documentSetId: Long, documentIds: Seq[Long]) = {
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id inSet documentIds)
  }

  private lazy val updateTitleCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
      .map(_.title)
  }

  private lazy val updateMetadataJsonCompiled = Compiled { (documentSetId: Rep[Long], documentId: Rep[Long]) =>
    Documents
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === documentId)
      .map(_.metadataJson)
  }

  private lazy val updatePdfNotesCompiled = Compiled { (documentId: Rep[Long]) =>
    import DocumentsImpl._

    Documents
      .filter(_.id === documentId)
      .map(_.pdfNotes)
  }

  private lazy val byId = Compiled { (documentId: Rep[Long]) =>
    Documents.filter(_.id === documentId)
  }
}

object DocumentBackend extends DbDocumentBackend {
  override protected val indexClient = com.overviewdocs.searchindex.ElasticSearchIndexClient.singleton
}
