package controllers.api

import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedRequest
import controllers.SelectionHelpers
import models.Selection

trait ApiSelectionHelpers extends SelectionHelpers { self: ApiController =>
  protected def requestToSelection(documentSetId: Long, request: ApiAuthorizedRequest[_]): Future[Either[Result,Selection]] = {
    requestToSelection(documentSetId, request.apiToken.createdBy, request)
  }
}
