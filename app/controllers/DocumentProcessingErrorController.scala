package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.DocumentProcessingError
import com.overviewdocs.models.tables.DocumentProcessingErrors
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet

class DocumentProcessingErrorController @Inject() (
  messagesApi: MessagesApi
) extends Controller(messagesApi) with HasDatabase {

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      errors <- findDocumentProcessingErrors(documentSetId)
    } yield {
      val byStatusMessage = errors.groupBy(_.statusMessage)
      val distinctMessages = byStatusMessage.keySet.toSeq.sorted
      val organizedErrors = distinctMessages.map(m => (m -> byStatusMessage(m)))
      Ok(views.html.DocumentProcessingError.index(organizedErrors))
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
