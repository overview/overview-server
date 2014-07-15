package controllers

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import models.OverviewDocumentProcessingError

trait DocumentProcessingErrorController extends Controller {

  def index(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val errors = findDocumentProcessingErrors(documentSetId)

    Ok(views.html.DocumentProcessingError.index(errors))
  }

  protected def findDocumentProcessingErrors(documentSetId: Long): Seq[(String, Seq[OverviewDocumentProcessingError])]
}

object DocumentProcessingErrorController extends DocumentProcessingErrorController {
  override protected def findDocumentProcessingErrors(documentSetId: Long): Seq[(String, Seq[OverviewDocumentProcessingError])] =
    OverviewDocumentProcessingError.sortedByStatus(documentSetId)
}