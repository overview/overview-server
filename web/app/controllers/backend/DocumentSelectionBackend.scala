package controllers.backend

import akka.stream.scaladsl.Sink
import akka.stream.Materializer
import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.libs.json.JsObject
import scala.collection.{immutable,mutable}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._ // TODO use another context

import com.overviewdocs.database.Database
import com.overviewdocs.models.{DocumentIdFilter,DocumentIdSet,DocumentSet}
import com.overviewdocs.query.{Field,Query=>SearchQuery} // conflicts with SQL Query
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
  *
  * onProgress() will be called with numbers between 0.0 and 1.0, inclusive. It
  * may be called at any time before the Future is resolved.
  */
@ImplementedBy(classOf[DbDocumentSelectionBackend])
trait DocumentSelectionBackend {
  def createSelection(selectionRequest: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection]
}

class DbDocumentSelectionBackend @Inject() (
  val database: Database,
  val searchBackend: SearchBackend,
  val documentSetBackend: DocumentSetBackend,
  val documentIdListBackend: DocumentIdListBackend,
  val materializer: Materializer
) extends DocumentSelectionBackend with DbBackend {
  import database.api._

  protected lazy val logger = Logger.forClass(getClass)

  /** Returns all a DocumentSet's Document IDs, sorted. */
  private def getSortedIds(documentSet: DocumentSet, sortByMetadataField: Option[String], onProgress: Double => Unit): Future[(Array[Long],List[SelectionWarning])] = {
    logger.logExecutionTimeAsync("fetching sorted document IDs [docset {}, field {}]", documentSet.id, sortByMetadataField) {
      sortByMetadataField match {
        case None => getDefaultSortedIds(documentSet.id).map(ids => (ids, Nil))
        case Some(field) if !validFieldNames(documentSet).contains(field) => {
          getDefaultSortedIds(documentSet.id)
            .map(ids => (ids, List(SelectionWarning.MissingField(field, validFieldNames(documentSet)))))
        }
        case Some(field) => {
          documentIdListBackend.showOrCreate(documentSet.id.toInt, field)
            .toMat(Sink.foreach(progress => onProgress(progress.progress)))((idsFuture, doneFuture) => doneFuture.flatMap(_ => idsFuture))
            .run()(materializer)
            .map(_ match {
              case None => throw new Exception("Sort failed. Look for earlier error messages.")
              case Some(ids) => (ids.toDocumentIdArray, Nil)
            })
        }
      }
    }
  }

  private def getDefaultSortedIds(documentSetId: Long): Future[Array[Long]] = {
    // The ORM is unaware of DocumentSet.sortedDocumentIds
    val q = sql"SELECT sorted_document_ids FROM document_set WHERE id = ${documentSetId}".as[Seq[Long]]
    database.option(q).map(_.map(_.toArray).getOrElse(Array.empty))
  }

  private def queryMetadataFieldNames(query: SearchQuery): immutable.Set[String] = {
    val fieldNames = mutable.Set.empty[String]
    SearchQuery.walkFields(query)(_ match {
      case Field.Metadata(name) => fieldNames.add(name)
      case _ => {}
    })
    fieldNames.to[immutable.Set]
  }

  private def validFieldNames(documentSet: DocumentSet): List[String] = {
    documentSet.metadataSchema.fields.map(_.name).toList
  }

  private def missingFields(documentSet: DocumentSet, fields: List[String]): List[String] = {
    fields.diff(validFieldNames(documentSet))
  }

  private def missingQueryFieldWarnings(documentSet: DocumentSet, query: SearchQuery): List[SelectionWarning] = {
    val queryFieldNames = queryMetadataFieldNames(query).toList.sorted
    missingFields(documentSet, queryFieldNames)
      .map(name => SelectionWarning.MissingField(name, validFieldNames(documentSet)))
  }

  /** Returns IDs from the searchindex */
  private def indexByQ(documentSet: DocumentSet, query: SearchQuery): Future[(DocumentIdFilter,List[SelectionWarning])] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", query.toString) {
      val documentSetWarnings = missingQueryFieldWarnings(documentSet, query)
      for {
        searchResult <- searchBackend.search(documentSet.id, query)
      } yield {
        val searchResultWarnings = searchResult.warnings.map(w => SelectionWarning.SearchIndexWarning(w))
        (searchResult.documentIds, documentSetWarnings ++ searchResultWarnings)
      }
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
  private def indexSelectedIds(request: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection] = {
    documentSetBackend.show(request.documentSetId).flatMap(_ match {
      case None => Future.successful(InMemorySelection(Array.empty[Long]))
      case Some(documentSet) => {
        val byQFuture: Future[(DocumentIdFilter,List[SelectionWarning])] = request.q match {
          case None => Future.successful((DocumentIdFilter.All(request.documentSetId.toInt), Nil))
          case Some(q) => indexByQ(documentSet, q)
        }

        val byDbFuture: Future[DocumentIdFilter] = if (request.copy(q=None).isAll) {
          Future.successful(DocumentIdFilter.All(request.documentSetId.toInt))
        } else {
          indexByDB(request)
        }

        for {
          (allSortedIds, sortWarnings) <- getSortedIds(documentSet, request.sortByMetadataField, onProgress)
          (byQ, byQWarnings) <- byQFuture
          byDb <- byDbFuture
        } yield {
          logger.logExecutionTime("filtering sorted document IDs [docset {}]", request.documentSetId) {
            val idsFilter = byQ.intersect(byDb)
            val sortedIds: Array[Long] = allSortedIds.filter(idsFilter.contains _)
            InMemorySelection(sortedIds, byQWarnings ++ sortWarnings)
          }
        }
      }
    })
  }

  override def createSelection(request: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection] = {
    indexSelectedIds(request, onProgress)
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
