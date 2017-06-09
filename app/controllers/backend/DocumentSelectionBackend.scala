package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.libs.json.JsObject
import scala.collection.{immutable,mutable}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._ // TODO use another context

import com.overviewdocs.models.DocumentIdSet
import com.overviewdocs.query.{Query=>SearchQuery} // conflicts with SQL Query
import com.overviewdocs.searchindex.SearchResult
import com.overviewdocs.util.Logger
import models.{InMemorySelection,SelectionRequest,SelectionWarning}

/** Constructs an InMemorySelection out of a SelectionRequest.
  *
  * Do not confuse this with other Backends:
  *
  * SelectionBackend loads and saves Selections.
  * DocumentBackend loads and saves Documents.
  * DocumentSelectionBackend creates a Selection by querying services.
  */
@ImplementedBy(classOf[DbDocumentSelectionBackend])
trait DocumentSelectionBackend {
  def createSelection(selectionRequest: SelectionRequest): Future[InMemorySelection]
}

class DbDocumentSelectionBackend @Inject() (
  val searchBackend: SearchBackend
) extends DocumentSelectionBackend with DbBackend {
  import database.api._

  protected lazy val logger = Logger.forClass(getClass)

  /** Returns all a DocumentSet's Document IDs, sorted. */
  private def indexAllIds(documentSetId: Long): Future[Array[Long]] = {
    logger.logExecutionTimeAsync("fetching sorted document IDs [docset {}]", documentSetId) {
      database.option(sortedIds(documentSetId)).map(_.map(_.toArray).getOrElse(Array.empty))
    }
  }

  /** Returns IDs from the searchindex */
  private def indexByQ(documentSetId: Long, query: SearchQuery): Future[SearchResult] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", query.toString) {
      searchBackend.search(documentSetId, query)
    }
  }

  /** Returns IDs that match the given tags/objects/etc (everything but
    * search pharse), unsorted.
    */
  private def indexByDB(request: SelectionRequest): Future[DocumentIdSet] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", request.toString) {
      implicit val getInt = slick.jdbc.GetResult(r => r.nextInt)

      database.run(sql"#${idsBySelectionRequestSql(request)}".as[Int]).map { result =>
        val ids = mutable.BitSet.empty
        result.foreach(ids.add _)
        DocumentIdSet(request.documentSetId.toInt, immutable.BitSet.fromBitMaskNoCopy(ids.toBitMask))
      }
    }
  }

  /** Returns a subset of the DocumentSet's Document IDs, sorted. */
  private def indexSelectedIds(request: SelectionRequest): Future[InMemorySelection] = {
    val byQFuture: Future[Option[SearchResult]] = request.q match {
      case None => Future.successful(None)
      case Some(q) => indexByQ(request.documentSetId, q).map(r => Some(r))
    }

    val byDbFuture: Future[Option[DocumentIdSet]] = if (request.copy(q=None).isAll) {
      Future.successful(None)
    } else {
      indexByDB(request).map(r => Some(r))
    }

    for {
      allSortedIds <- indexAllIds(request.documentSetId)
      byQ <- byQFuture
      byDb <- byDbFuture
    } yield {
      logger.logExecutionTime("filtering sorted document IDs [docset {}]", request.documentSetId) {
        (byQ, byDb) match {
          case (None, None) => {
            InMemorySelection(allSortedIds)
          }
          case (Some(searchResponse), None) => {
            InMemorySelection(
              allSortedIds.filter(id => searchResponse.documentIds.lowerIds.contains(id.toInt)),
              searchResponse.warnings.map(w => SelectionWarning.SearchIndexWarning(w))
            )
          }
          case (None, Some(dbIds)) => {
            InMemorySelection(allSortedIds.filter(id => dbIds.lowerIds.contains(id.toInt)))
          }
          case (Some(searchResponse), Some(dbIds)) => {
            val ids = searchResponse.documentIds.intersect(dbIds)
            InMemorySelection(
              allSortedIds.filter(id => ids.lowerIds.contains(id.toInt)),
              searchResponse.warnings.map(w => SelectionWarning.SearchIndexWarning(w))
            )
          }
        }
      }
    }
  }

  override def createSelection(request: SelectionRequest): Future[InMemorySelection] = {
    if (request.isAll) {
      indexAllIds(request.documentSetId).map(ids => InMemorySelection(ids))
    } else {
      indexSelectedIds(request)
    }
  }

  protected def sortedIds(documentSetId: Long): DBIO[Seq[Seq[Long]]] = {
    // The ORM is unaware of DocumentSet.sortedDocumentIds
    sql"SELECT sorted_document_ids FROM document_set WHERE id = ${documentSetId}".as[Seq[Long]]
  }

  protected def idsBySelectionRequestSql(request: SelectionRequest): String = {
    // Don't have to worry about SQL injection: every SelectionRequest
    // parameter is an ID. (Or it's "q", which this method ignores.)
    val sb = new StringBuilder(s"""SELECT id::BIT(32)::INT4 FROM document WHERE document_set_id = ${request.documentSetId}""")

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
          val parts = mutable.Buffer[String]()

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
}
