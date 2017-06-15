package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet,userOwningFileGroup}
import controllers.backend.{DocumentSetBackend,FileGroupBackend,ImportJobBackend}
import controllers.util.JobQueueSender

class FileImportController @Inject() (
  documentSetBackend: DocumentSetBackend,
  jobQueueSender: JobQueueSender,
  importJobBackend: ImportJobBackend,
  fileGroupBackend: FileGroupBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {

  def _new = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(views.html.FileImport._new(request.user, count))
  }

  def edit(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val documentSetFuture = documentSetBackend.show(documentSetId)
      .map(_.getOrElse(throw new Exception(s"DocumentSet $documentSetId disappeared!")))

    for {
      documentSet <- documentSetFuture
      count <- documentSetBackend.countByOwnerEmail(request.user.email)
    } yield Ok(views.html.FileImport.edit(request.user, documentSet, count))
  }

  /** Deletes a FileGroup, potentially cancelling any worker-side operations.
    *
    * There are many possibilities:
    *
    * * The FileGroup does not exist (or does not belong to the user, or it is
    *   deleted): return auth error.
    * * The FileGroup exists, but a race makes it disappear before we can
    *   fetch it from the database: return `NoContent`.
    * * The FileGroup exists and has an `addToDocumentSetId`: set
    *   `deleted=true`, notify the worker, and return `NoContent`.
    * * The FileGroup exists and has no `addToDocumentSetId`: undefined
    *   behavior.
    */
  def delete(fileGroupId: Long) = AuthorizedAction(userOwningFileGroup(fileGroupId)).async { implicit request =>
    fileGroupBackend.find(fileGroupId).map(_.map(_.addToDocumentSetId)).flatMap(_ match {
      case None => Future.successful(NoContent)
      case Some(None) => Future.successful(
        BadRequest("This FileGroup has no addToDocumentSetId; whatever linked here is broken")
      )
      case Some(Some(documentSetId)) => {
        fileGroupBackend.destroy(fileGroupId).map { _ =>
          val command = DocumentSetCommands.CancelAddDocumentsFromFileGroup(documentSetId, fileGroupId)
          jobQueueSender.send(command)
          NoContent
        }
      }
    })
  }
}
