package controllers.api

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNumber}
import play.api.mvc.{RequestHeader,Result}
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{Selection,SelectionRequest}

trait DocumentController extends ApiController with ApiSelectionHelpers {
  protected val documentBackend: DocumentBackend

  private def _indexDocuments(selection: Selection, pageRequest: PageRequest, fields: Set[String]): Future[Result] = {
    for { documents <- documentBackend.index(selection, pageRequest, fields.contains("text")) }
    yield Ok(views.json.api.DocumentHeader.index(selection.id, documents, fields))
  }

  private def _streamDocumentsInner(selection: Selection, pageRequest: PageRequest, fields: Set[String], documentCount: Int): Result = {
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

    val content = Enumerator(s"""{"selectionId":"${selection.id.toString}","pagination":{"offset":${pageRequest.offset},"limit":${pageRequest.limit},"total":${documentCount}},"items":[""")
      .andThen(jsObjectChunks)
      .andThen(Enumerator("]}"))

    Ok.chunked(content)
      .as("application/json")
  }

  private def _streamDocuments(selection: Selection, pageRequest: PageRequest, fields: Set[String]): Future[Result] = {
    for {
      documentCount <- selection.getDocumentCount
    } yield _streamDocumentsInner(selection, pageRequest, fields, documentCount)
  }

  private def _indexIds(selection: Selection): Future[Result] = {
    for { ids <- selection.getAllDocumentIds }
    yield Ok(JsArray(ids.map(JsNumber(_))))
  }

  private def parseFields(fields: String): Set[String] = {
    fields match {
      case "" => Set("id", "keywords", "pageNumber", "suppliedId", "title", "url")
      case _ => Set("id") ++ fields.split(",").toSet.intersect(DocumentController.ValidFields)
    }
  }

  def index(documentSetId: Long, fields: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        val fieldSet = parseFields(fields)

        if (fieldSet.size == 1) {
          _indexIds(selection)
        } else {
          val streaming = RequestData(request).getBoolean("stream").getOrElse(false)

          val pageLimit = if (streaming) {
            Int.MaxValue
          } else if (fieldSet.contains("text")) {
            DocumentController.MaxTextPageLimit
          } else {
            DocumentController.MaxPageLimit
          }
          val pr = pageRequest(request, pageLimit)

          if (streaming) {
            _streamDocuments(selection, pr, fieldSet)
          } else {
            _indexDocuments(selection, pr, fieldSet)
          }
        }
      }
    })
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
