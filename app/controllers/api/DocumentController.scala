package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNumber}
import play.api.mvc.RequestHeader
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{SelectionLike,SelectionRequest}

trait DocumentController extends ApiController {
  protected val documentBackend: DocumentBackend
  protected val selectionBackend: SelectionBackend

  private def _indexDocuments(userEmail: String, selectionRequest: SelectionRequest, pageRequest: PageRequest, fields: Set[String]) = {
    val selection: Future[SelectionLike] = pageRequest.offset match {
      case 0 => selectionBackend.create(userEmail, selectionRequest)
      case _ => selectionBackend.findOrCreate(userEmail, selectionRequest)
    }

    selection
      .flatMap(documentBackend.index(_, pageRequest, fields.contains("text")))
      .map { infos =>
        Ok(views.json.api.DocumentHeader.index(infos, fields))
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

    if (fieldSet.size == 1) {
      _indexIds(sr)
    } else {
      val pageLimit = if (fieldSet.contains("text")) {
        DocumentController.MaxTextPageLimit
      } else {
        DocumentController.MaxPageLimit
      }
      val pr = pageRequest(request, pageLimit)
      _indexDocuments(request.apiToken.createdBy, sr, pr, fieldSet)
    }
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
  private val MaxTextPageLimit = 10

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
