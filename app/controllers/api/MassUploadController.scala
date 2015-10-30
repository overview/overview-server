package controllers.api

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialAction,RequestHeader,Result}
import scala.concurrent.Future

import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.models.{ApiToken,FileGroup,GroupedFileUpload}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.tree.orm.DocumentSetCreationJob
import com.overviewdocs.util.ContentDisposition
import controllers.auth.{ApiAuthorizedAction,ApiTokenFactory}
import controllers.auth.Authorities.anyUser
import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import controllers.forms.MassUploadControllerForm
import controllers.iteratees.GroupedFileUploadIteratee
import controllers.util.{MassUploadControllerMethods,JobQueueSender}
import models.orm.stores.DocumentSetCreationJobStore

trait MassUploadController extends ApiController {
  protected val fileGroupBackend: FileGroupBackend
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val storage: MassUploadController.Storage
  protected val messageQueue: JobQueueSender
  protected val apiTokenFactory: ApiTokenFactory
  protected val uploadIterateeFactory: (GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit]

  def index = ApiAuthorizedAction(anyUser).async { request =>
    for {
      fileGroup <- fileGroupBackend.findOrCreate(FileGroup.CreateAttributes(request.apiToken.createdBy, Some(request.apiToken.token)))
      uploads <- groupedFileUploadBackend.index(fileGroup.id)
    } yield Ok(views.json.api.MassUpload.index(uploads))
  }

  /** Starts or resumes a file upload. */
  def create(guid: UUID) = EssentialAction { request =>
    val futureIteratee: Future[Iteratee[Array[Byte],Result]] = apiTokenFactory.loadAuthorizedApiToken(request, anyUser).map(_ match {
      case Left(result) => Iteratee.ignore.map(_ => result)
      case Right(apiToken) => MassUploadControllerMethods.Create(
        apiToken.createdBy,
        Some(apiToken.token),
        guid,
        fileGroupBackend,
        groupedFileUploadBackend,
        uploadIterateeFactory,
        true
      )(request)
    })

    Iteratee.flatten(futureIteratee)
  }

  /** Responds to a HEAD request.
    *
    * There are four possible responses:
    *
    * <ul>
    *   <li><tt>404 Not Found</tt>: the file with the given <tt>guid</tt> is
    *       not in the active file group.</li>
    *   <li><tt>204 No Content</tt>: there is an empty file with the given
    *       <tt>guid</tt>. It may or may not be completely uploaded. This
    *       will include a <tt>Content-Disposition</tt> header.</li>
    *   <li><tt>206 Partial Content</tt>: the file is partially uploaded. This
    *       will include <tt>Content-Disposition</tt> and
    *       <tt>Content-Range</tt> headers.</li>
    *   <li><tt>200 OK</tt>: the file is fully uploaded and non-empty. This
    *       will include <tt>Content-Disposition</tt> and
    *       <tt>Content-Length</tt> headers.</li>
    * </ul>
    *
    * Regardless of the status code, the body will be empty.
    *
    * TODO refactor into MassUploadControllerMethods
    */
  def show(guid: UUID) = ApiAuthorizedAction(anyUser).async { request =>
    def contentDisposition(upload: GroupedFileUpload) = {
      ContentDisposition.fromFilename(upload.name).contentDisposition
    }

    def uploadHeaders(upload: GroupedFileUpload): Seq[(String, String)] = {
      def computeEnd(uploadedSize: Long): Long =
        if (upload.uploadedSize == 0) 0
        else upload.uploadedSize - 1

      Seq(
        (CONTENT_LENGTH, s"${upload.uploadedSize}"),
        (CONTENT_RANGE, s"bytes 0-${computeEnd(upload.uploadedSize)}/${upload.size}"),
        (CONTENT_DISPOSITION, contentDisposition(upload)))
    }

    findUploadInCurrentFileGroup(request.apiToken.createdBy, request.apiToken.token, guid).map(_ match {
      case None => NotFound
      case Some(u) if (u.uploadedSize == 0L) => {
        // You can't send an Ok or PartialContent when Content-Length=0
        NoContent.withHeaders((CONTENT_DISPOSITION, contentDisposition(u)))
      }
      case Some(u) if (u.uploadedSize == u.size) => Ok.withHeaders(uploadHeaders(u): _*)
      case Some(u) => PartialContent.withHeaders(uploadHeaders(u): _*)
    })
  }

  /** Marks the FileGroup as <tt>completed</tt> and kicks off a
    * DocumentSetCreationJob.
    *
    * TODO refactor into MassUploadControllerMethods
    */
  def startClustering = ApiAuthorizedAction(anyUser).async { request =>
    MassUploadControllerForm.edit.bindFromRequest()(request).fold(
      e => Future(BadRequest),
      startClusteringFileGroupWithOptions(request.apiToken, _)
    )
  }

  /** Cancels the upload and notify the worker to delete all uploaded files
    *
    * TODO refactor into MassUploadControllerMethods
    */
  def cancel = ApiAuthorizedAction(anyUser).async { request =>
    fileGroupBackend.find(request.apiToken.createdBy, Some(request.apiToken.token))
      .flatMap(_ match {
        case Some(fileGroup) => fileGroupBackend.destroy(fileGroup.id)
        case None => Future.successful(())
      })
      .map(_ => Accepted)
  }

  private def findUploadInCurrentFileGroup(userEmail: String, apiToken: String, guid: UUID): Future[Option[GroupedFileUpload]] = {
    fileGroupBackend.find(userEmail, Some(apiToken))
      .flatMap(_ match {
        case None => Future.successful(None)
        case Some(fileGroup) => groupedFileUploadBackend.find(fileGroup.id, guid)
      })
  }

  private def startClusteringFileGroupWithOptions(apiToken: ApiToken,
                                                  options: (String, Boolean, String, String)): Future[Result] = {
    val userEmail: String = apiToken.createdBy
    // FIXME type-unsafe .get. Change the URL.
    val documentSetId: Long = apiToken.documentSetId.get
    val (lang, splitDocuments, suppliedStopWords, importantWords) = options

    fileGroupBackend.find(userEmail, Some(apiToken.token)).flatMap(_ match {
      case Some(fileGroup) => {
        val job: DocumentSetCreationJob = /*DeprecatedDatabase.inTransaction*/ {
          storage.createMassUploadDocumentSetCreationJob(
            documentSetId, fileGroup.id, lang, splitDocuments, suppliedStopWords, importantWords)
        }

        fileGroupBackend.update(fileGroup.id, true) // TODO put in transaction
          .map(_ => Created)
      }
      case None => Future.successful(NotFound)
    })
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {
  override protected val storage = DatabaseStorage
  override protected val messageQueue = JobQueueSender
  override protected val fileGroupBackend = FileGroupBackend
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
  override protected val apiTokenFactory = ApiTokenFactory
  override protected val uploadIterateeFactory = GroupedFileUploadIteratee.apply _

  trait Storage {
    /** @returns a newly created DocumentSetCreationJob */
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, splitDocuments: Boolean,
                                               suppliedStopWords: String, importantWords: String): DocumentSetCreationJob
  }

  object DatabaseStorage extends Storage {
    override def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long,
                                                        lang: String, splitDocuments: Boolean,
                                                        suppliedStopWords: String,
                                                        importantWords: String): DocumentSetCreationJob = {
      import com.overviewdocs.tree.orm.DocumentSetCreationJobState.FilesUploaded
      import com.overviewdocs.tree.DocumentSetCreationJobType.FileUpload

      val job = DeprecatedDatabase.inTransaction {
        DocumentSetCreationJobStore.insertOrUpdate(
          DocumentSetCreationJob(
            documentSetId = documentSetId,
            fileGroupId = Some(fileGroupId),
            lang = lang,
            splitDocuments = splitDocuments,
            suppliedStopWords = suppliedStopWords,
            importantWords = importantWords,
            state = FilesUploaded,
            jobType = FileUpload,
            canBeCancelled = false
          )
        )
      }

      val command = DocumentSetCommands.AddDocumentsFromFileGroup(
        job.id,
        documentSetId=job.documentSetId,
        fileGroupId=job.fileGroupId.get,
        lang=job.lang,
        splitDocuments=job.splitDocuments
      )

      JobQueueSender.send(command)

      job
    }
  }
}
