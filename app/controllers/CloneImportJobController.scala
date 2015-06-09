package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.DocumentSetBackend
import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType,UploadedFile}
import org.overviewproject.models.tables.{DocumentSetCreationJobs,UploadedFiles}

trait CloneImportJobController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  def create(sourceDocumentSetId: Long) = AuthorizedAction(userViewingDocumentSet(sourceDocumentSetId)).async { implicit request =>
    documentSetBackend.show(sourceDocumentSetId).flatMap(_ match {
      case None => Future.successful(NotFound) // Extremely unlikely race
      case Some(originalDocumentSet) => {
        for {
          cloneDocumentSet <- CloneImportJobController.clone(originalDocumentSet, request.user.email)
        } yield Redirect(routes.DocumentSetController.show(cloneDocumentSet.id))
          .flashing("event" -> "document-set-create-clone")
      }
    })
  }
}

object CloneImportJobController extends CloneImportJobController with DatabaseProvider {
  import databaseApi._

  override protected val documentSetBackend = DocumentSetBackend

  private val UploadedFileInserter = (UploadedFiles.map(_.createAttributes) returning UploadedFiles.map(_.id))
  private val JobInserter = DocumentSetCreationJobs.map(_.createAttributes)

  private def clone(originalDocumentSet: DocumentSet, userEmail: String): Future[DocumentSet] = {
    database.run(for {
      maybeUploadedFileId <- maybeCloneUploadedFileId(originalDocumentSet.uploadedFileId)
      documentSet <- DBIO.from(documentSetBackend.create(cloneAttributes(originalDocumentSet, maybeUploadedFileId), userEmail))
      _ <- JobInserter.+=(buildJob(documentSet, originalDocumentSet.id))
    } yield documentSet)
  }

  private def maybeCloneUploadedFileId(maybeFileId: Option[Long]): DBIO[Option[Long]] = {
    maybeFileId match {
      case None => DBIO.successful(None)
      case Some(originalUploadedFileId) => for {
        originalUploadedFile <- UploadedFiles.filter(_.id === originalUploadedFileId).result.head
        cloneFileId <- UploadedFileInserter.+=(originalUploadedFile.toCreateAttributes)
      } yield Some(cloneFileId)
    }
  }

  private def cloneAttributes(documentSet: DocumentSet, maybeUploadedFileId: Option[Long]) = {
    DocumentSet.CreateAttributes(
      title=documentSet.title,
      query=documentSet.query,
      documentCount=documentSet.documentCount,
      documentProcessingErrorCount=documentSet.documentProcessingErrorCount,
      importOverflowCount=documentSet.importOverflowCount,
      uploadedFileId=maybeUploadedFileId
    )
  }

  private def buildJob(documentSet: DocumentSet, sourceDocumentSetId: Long) = DocumentSetCreationJob.CreateAttributes(
    documentSetId=documentSet.id,
    jobType=DocumentSetCreationJobType.Clone,
    retryAttempts=0,
    lang="en",                  // doesn't matter for clone
    suppliedStopWords="",       // doesn't matter for clone
    importantWords="",          // doesn't matter for clone
    splitDocuments=false,       // doesn't matter for clone
    documentcloudUsername=None, // doesn't matter for clone
    documentcloudPassword=None, // doesn't matter for clone
    contentsOid=None,           // doesn't matter for clone
    fileGroupId=None,           // doesn't matter for clone
    sourceDocumentSetId=Some(sourceDocumentSetId),
    treeTitle=None,             // doesn't matter for clone
    treeDescription=None,       // doesn't matter for clone
    tagId=None,                 // doesn't matter for clone
    state=DocumentSetCreationJobState.NotStarted,
    fractionComplete=0,
    statusDescription="",
    canBeCancelled=true
  )
}
