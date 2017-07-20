package controllers.api

import play.api.i18n.Messages
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedRequest
import controllers.util.NullMessagesApi
import controllers.SelectionHelpers
import models.Selection

trait ApiSelectionHelpers extends SelectionHelpers { self: ApiBaseController =>
  implicit val messages: Messages = NullMessagesApi.messages

  protected def requestToSelection(documentSetId: Long, request: ApiAuthorizedRequest[_]): Future[Either[Result,Selection]] = {
    requestToSelection(documentSetId, request.apiToken.createdBy, request)
  }
}
