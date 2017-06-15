package controllers.api

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import java.util.UUID
import javax.inject.Inject
import play.api.libs.streams.Accumulator
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{EssentialAction,RequestHeader,Result}
import scala.concurrent.Future

import com.overviewdocs.models.{ApiToken,FileGroup,GroupedFileUpload}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.ContentDisposition
import controllers.auth.{ApiAuthorizedAction,ApiTokenFactory}
import controllers.auth.Authorities.anyUser
import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import controllers.forms.MassUploadControllerForm
import controllers.iteratees.GroupedFileUploadIteratee
import controllers.util.{MassUploadControllerMethods,JobQueueSender}

class MassUploadController @Inject() (
  fileGroupBackend: FileGroupBackend,
  jobQueueSender: JobQueueSender,
  groupedFileUploadBackend: GroupedFileUploadBackend,
  apiTokenFactory: ApiTokenFactory,
  uploadSinkFactory: MassUploadControllerMethods.UploadSinkFactory
) extends ApiController {

  def index = ApiAuthorizedAction(anyUser).async { request =>
    for {
      fileGroup <- fileGroupBackend.findOrCreate(FileGroup.CreateAttributes(request.apiToken.createdBy, Some(request.apiToken.token)))
      uploads <- groupedFileUploadBackend.index(fileGroup.id)
    } yield Ok(views.json.api.MassUpload.index(uploads))
  }

  /** Starts or resumes a file upload. */
  def create(guid: UUID) = EssentialAction { request =>
    val futureAccumulator: Future[Accumulator[ByteString,Result]] = apiTokenFactory.loadAuthorizedApiToken(request, anyUser).map(_ match {
      case Left(result) => Accumulator(Sink.ignore).map(_ => result)
      case Right(apiToken) => MassUploadControllerMethods.Create(
        apiToken.createdBy,
        Some(apiToken.token),
        guid,
        fileGroupBackend,
        groupedFileUploadBackend,
        uploadSinkFactory,
        true
      )(request)
    })

    Accumulator.flatten(futureAccumulator)(play.api.Play.current.materializer)
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

  /** Marks the FileGroup as <tt>completed</tt> and pokes the worker.
    *
    * TODO refactor into MassUploadControllerMethods
    */
  def startClustering = ApiAuthorizedAction(anyUser).async { request =>
    MassUploadControllerForm.edit.bindFromRequest()(request).fold(
      e => Future(BadRequest),
      values => {
        val userEmail: String = request.apiToken.createdBy
        val documentSetId: Long = request.apiToken.documentSetId.get // FIXME type-unsafe .get. Change the URL.
        val (lang, splitDocuments, ocr, metadataJson) = values

        fileGroupBackend.find(userEmail, Some(request.apiToken.token)).map(_.map(_.id)).flatMap(_ match {
          case None => Future.successful(NotFound)
          case Some(fileGroupId) => {
            for {
              fileGroup <- fileGroupBackend.addToDocumentSet(
                fileGroupId,
                documentSetId,
                lang,
                splitDocuments,
                ocr,
                metadataJson
              ).map(_.get)
            } yield {
              jobQueueSender.send(DocumentSetCommands.AddDocumentsFromFileGroup(fileGroup))
              Created
            }
          }
        })
      }
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
}
