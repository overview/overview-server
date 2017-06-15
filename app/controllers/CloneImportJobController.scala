package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{CloneJob,DocumentSet}
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userViewingDocumentSet}
import controllers.backend.{CloneJobBackend,DocumentSetBackend}
import controllers.util.JobQueueSender

class CloneImportJobController @Inject() (
  cloneJobBackend: CloneJobBackend,
  documentSetBackend: DocumentSetBackend,
  jobQueueSender: JobQueueSender,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  def create(sourceDocumentSetId: Long) = AuthorizedAction(userViewingDocumentSet(sourceDocumentSetId)).async { implicit request =>
    documentSetBackend.show(sourceDocumentSetId).flatMap(_ match {
      case None => Future.successful(NotFound) // Extremely unlikely race
      case Some(originalDocumentSet) => {
        for {
          documentSet <- documentSetBackend.create(cloneAttributes(originalDocumentSet), request.user.email)
          cloneJob <- cloneJobBackend.create(CloneJob.CreateAttributes(originalDocumentSet.id, documentSet.id))
        } yield {
          jobQueueSender.send(DocumentSetCommands.CloneDocumentSet(cloneJob))
          Redirect(routes.DocumentSetController.show(documentSet.id))
            .flashing("event" -> "document-set-create-clone")
        }
      }
    })
  }

  def delete(documentSetId: Long, cloneJobId: Int) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    for {
      _ <- cloneJobBackend.cancel(documentSetId, cloneJobId)
    } yield Redirect(routes.DocumentSetController.show(documentSetId))
  }

  private def cloneAttributes(documentSet: DocumentSet) = DocumentSet.CreateAttributes(
    title=documentSet.title,
    query=documentSet.query,
    documentCount=documentSet.documentCount,
    documentProcessingErrorCount=documentSet.documentProcessingErrorCount,
    importOverflowCount=documentSet.importOverflowCount,
    metadataSchema=documentSet.metadataSchema
  )
}
