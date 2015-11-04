package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.DocumentProcessingError
import com.overviewdocs.models.tables.DocumentProcessingErrors
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet

trait DocumentProcessingErrorController extends Controller {

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

  protected def findDocumentProcessingErrors(documentSetId: Long): Future[Seq[DocumentProcessingError]]
}

object DocumentProcessingErrorController extends DocumentProcessingErrorController with HasDatabase {
  import database.api._

  lazy val byDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    DocumentProcessingErrors
      .filter(_.documentSetId === documentSetId)
      .sortBy(dpe => (dpe.message, dpe.textUrl))
  }

  override protected def findDocumentProcessingErrors(documentSetId: Long) = {
    database.seq(byDocumentSetId(documentSetId))
  }
}
