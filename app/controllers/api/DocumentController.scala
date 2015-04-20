package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNumber}
import play.api.mvc.RequestHeader
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.exceptions.SearchParseFailed
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{Selection,SelectionRequest}

trait DocumentController extends ApiController {
  protected val documentBackend: DocumentBackend
  protected val selectionBackend: SelectionBackend

  private def _indexDocuments(userEmail: String, selectionRequest: SelectionRequest, pageRequest: PageRequest, fields: Set[String]) = {
    val selectionFuture: Future[Selection] = pageRequest.offset match {
      case 0 => selectionBackend.create(userEmail, selectionRequest)
      case _ => selectionBackend.findOrCreate(userEmail, selectionRequest)
    }

    for {
      selection <- selectionFuture
      documents <- documentBackend.index(selection, pageRequest, fields.contains("text"))
    } yield Ok(views.json.api.DocumentHeader.index(documents, fields))
  }

  private def _streamDocuments(userEmail: String, selectionRequest: SelectionRequest, pageRequest: PageRequest, fields: Set[String]) = {
    for {
      selection <- selectionBackend.create(userEmail, selectionRequest)
      documentCount <- selection.getDocumentCount
    } yield {
      import play.api.libs.iteratee._

      val start = pageRequest.offset
      val end = scala.math.min(start + pageRequest.limit, documentCount)
      val batchSize = DocumentController.StreamingPageLimit

      def fetchPage(pageStart: Int): Future[String] = {
        import play.api.libs.iteratee.Execution.defaultExecutionContext
        documentBackend.index(selection, PageRequest(pageStart, batchSize), fields.contains("text"))
          .map { (documents) =>
            val initialComma = if (pageStart != start && documents.items.nonEmpty) "," else ""
            val jsObjects = documents.items.map { (document) =>
              views.json.api.DocumentHeader.show(document, fields)
            }
            s"${initialComma}${jsObjects.map(_.toString).mkString(",")}"
          }
      }

      val jsObjectChunks = Enumerator.unfoldM[Int,String](start) { (pageStart) =>
        if (pageStart < end) {
          fetchPage(pageStart).map(Some(pageStart + batchSize, _))
        } else {
          Future.successful(None)
        }
      }

      val content = Enumerator(s"""{"pagination":{"offset":${pageRequest.offset},"limit":${pageRequest.limit},"total":${documentCount}},"items":[""")
        .andThen(jsObjectChunks)
        .andThen(Enumerator("]}"))

      Ok.chunked(content)
        .as("application/json")
    }
  }

  private def _indexIds(selectionRequest: SelectionRequest) = {
    documentBackend.indexIds(selectionRequest).map { ids =>
      Ok(JsArray(ids.map(JsNumber(_))))
    }
  }

  private def parseFields(fields: String): Set[String] = {
    fields match {
      case "" => Set("id", "keywords", "pageNumber", "suppliedId", "title", "url")
      case _ => Set("id") ++ fields.split(",").toSet.intersect(DocumentController.ValidFields)
    }
  }

  def index(documentSetId: Long, fields: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    val sr = selectionRequest(documentSetId, request)
    val fieldSet = parseFields(fields)

    val responseFuture = if (fieldSet.size == 1) {
      _indexIds(sr)
    } else {
      val streaming = request.queryString.get("stream").flatMap(_.headOption) == Some("true")

      val pageLimit = if (streaming) {
        Int.MaxValue
      } else if (fieldSet.contains("text")) {
        DocumentController.MaxTextPageLimit
      } else {
        DocumentController.MaxPageLimit
      }
      val pr = pageRequest(request, pageLimit)

      if (streaming) {
        _streamDocuments(request.apiToken.createdBy, sr, pr, fieldSet)
      } else {
        _indexDocuments(request.apiToken.createdBy, sr, pr, fieldSet)
      }
    }

    responseFuture.recover { case spf: SearchParseFailed => BadRequest(jsonError(spf.getMessage)) }
  }

  def show(documentSetId: Long, documentId: Long) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    documentBackend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(views.json.api.Document.show(document))
      case None => NotFound(jsonError(s"Document $documentId not found in document set $documentSetId"))
    })
  }
}

object DocumentController extends DocumentController {
  override protected val documentBackend = DocumentBackend
  override protected val selectionBackend = SelectionBackend

  private val MaxPageLimit = 1000
  private val MaxTextPageLimit = 20
  private val StreamingPageLimit = 20

  private val ValidFields = Set(
    "id",
    "documentSetId",
    "keywords",
    "pageNumber",
    "suppliedId",
    "text",
    "title",
    "url"
  )
}
