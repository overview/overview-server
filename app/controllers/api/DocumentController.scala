package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNumber}
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DbDocumentBackend,DocumentBackend}
import models.pagination.PageRequest

trait DocumentController extends ApiController {
  protected val backend: DocumentBackend

  private def _indexInfos(documentSetId: Long, q: String, pageRequest: PageRequest) = {
    backend.index(documentSetId, q, pageRequest).map { infos =>
      Ok(views.json.api.DocumentInfo.index(infos))
    }
  }

  private def _indexIds(documentSetId: Long, q: String) = {
    backend.indexIds(documentSetId, q).map { ids =>
      Ok(JsArray(ids.map(JsNumber(_))))
    }
  }

  def index(documentSetId: Long, q: String, fields: String) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    fields match {
      case "id" => _indexIds(documentSetId, q)
      case "" => _indexInfos(documentSetId, q, pageRequest(request, 1000))
      case _ => Future.successful(BadRequest(jsonError("""The "fields" parameter must be either "id" or "" for now. Sorry!""")))
    }
  }

  def show(documentSetId: Long, documentId: Long) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    backend.show(documentSetId, documentId).map(_ match {
      case Some(document) => Ok(views.json.api.Document.show(document))
      case None => NotFound(jsonError(s"Document $documentId not found in document set $documentSetId"))
    })
  }
}

object DocumentController extends DocumentController {
  override protected val backend = DocumentBackend
}
