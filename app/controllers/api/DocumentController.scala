package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNumber}
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,SelectionBackend}
import models.pagination.PageRequest
import models.{SelectionLike,SelectionRequest}

trait DocumentController extends ApiController {
  protected val documentBackend: DocumentBackend
  protected val selectionBackend: SelectionBackend

  private def _indexInfos(userEmail: String, selectionRequest: SelectionRequest, pageRequest: PageRequest) = {
    val selection: Future[SelectionLike] = pageRequest.offset match {
      case 0 => selectionBackend.create(userEmail, selectionRequest)
      case _ => selectionBackend.findOrCreate(userEmail, selectionRequest)
    }

    selection
      .flatMap(documentBackend.index(_, pageRequest))
      .map { infos =>
        Ok(views.json.api.DocumentInfo.index(infos))
      }
  }

  private def _indexIds(selectionRequest: SelectionRequest) = {
    documentBackend.indexIds(selectionRequest).map { ids =>
      Ok(JsArray(ids.map(JsNumber(_))))
    }
  }

  def index(documentSetId: Long, fields: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    val sr = selectionRequest(documentSetId, request)
    fields match {
      case "id" => _indexIds(sr)
      case "" => _indexInfos(request.apiToken.createdBy, sr, pageRequest(request, 1000))
      case _ => Future.successful(BadRequest(jsonError("""The "fields" parameter must be either "id" or "" for now. Sorry!""")))
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
}
