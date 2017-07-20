package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.DocumentProcessingError
import com.overviewdocs.models.tables.DocumentProcessingErrors
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet

class DocumentProcessingErrorController @Inject() (
  val controllerComponents: ControllerComponents,
  indexHtml: views.html.DocumentProcessingError.index
) extends BaseController with HasDatabase {

  def index(documentSetId: Long) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      errors <- findDocumentProcessingErrors(documentSetId)
    } yield {
      val byStatusMessage = errors.groupBy(_.statusMessage)
      val distinctMessages = byStatusMessage.keySet.toSeq.sorted
      val organizedErrors = distinctMessages.map(m => (m -> byStatusMessage(m)))
      Ok(indexHtml(organizedErrors))
    }
  }

  // TODO dependeny-inject a backend for this
  import database.api._
  private lazy val byDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    DocumentProcessingErrors
      .filter(_.documentSetId === documentSetId)
      .sortBy(dpe => (dpe.message, dpe.textUrl))
  }

  private def findDocumentProcessingErrors(documentSetId: Long): Future[Seq[DocumentProcessingError]] = {
    database.seq(byDocumentSetId(documentSetId))
  }
}
