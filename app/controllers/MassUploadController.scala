package controllers

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{Action,BodyParser,Result}
import scala.concurrent.{Future,blocking}

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{DocumentSet,GroupedFileUpload}
import com.overviewdocs.util.ContentDisposition
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.auth.{AuthorizedAction,AuthorizedBodyParser}
import controllers.backend.{DocumentSetBackend,FileGroupBackend,GroupedFileUploadBackend}
import controllers.forms.MassUploadControllerForm
import controllers.iteratees.GroupedFileUploadIteratee
import controllers.util.{MassUploadControllerMethods,JobQueueSender}
import models.User

trait MassUploadController extends Controller {
  protected val documentSetBackend: DocumentSetBackend
  protected val fileGroupBackend: FileGroupBackend
  protected val jobQueueSender: JobQueueSender
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val uploadIterateeFactory: (GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit]

  /** Calls MassUploadControllerMethods.Create(), returning the result as body.
    *
    * Used in create().
    */
  private def createInnerBodyParser(user: User, guid: UUID): BodyParser[Result] = {
    BodyParser("MassUploadController.createInnerBodyParser") { request =>
      MassUploadControllerMethods.Create(
        user.email,
        None,
        guid,
        fileGroupBackend,
        groupedFileUploadBackend,
        uploadIterateeFactory,
        false
      )(request)
        .map(result => Right(result)) // Doesn't matter which it is...
    }
  }

  /** Checks user auth, then calls createInnerBodyParser().
    *
    * Used in create().
    */
  private def createAuthBodyParser(guid: UUID): BodyParser[Result] = {
    AuthorizedBodyParser(anyUser)(user => createInnerBodyParser(user, guid))
  }

  /** Starts or resumes a file upload. */
  def create(guid: UUID) = Action(createAuthBodyParser(guid))(finishedRequest => finishedRequest.body)

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
  def show(guid: UUID) = AuthorizedAction(anyUser).async { request =>
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

    findUploadInCurrentFileGroup(request.user.email, guid).map(_ match {
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
  def startClustering = AuthorizedAction(anyUser).async { request =>
    MassUploadControllerForm.new_.bindFromRequest()(request).fold(
      e => Future(BadRequest),
      values => {
        val (name, lang, splitDocuments, metadataJson) = values

        fileGroupBackend.find(request.user.email, None).map(_.map(_.id)).flatMap(_ match {
          case None => Future.successful(NotFound)
          case Some(fileGroupId) => {
            for {
              documentSet <- documentSetBackend.create(DocumentSet.CreateAttributes(name), request.user.email)
              fileGroup <- fileGroupBackend.addToDocumentSet(
                fileGroupId,
                documentSet.id,
                lang,
                splitDocuments,
                metadataJson
              ).map(_.get)
            } yield {
              jobQueueSender.send(DocumentSetCommands.AddDocumentsFromFileGroup(fileGroup))
              Redirect(routes.DocumentSetController.show(documentSet.id))
            }
          }
        })
      }
    )
  }

  /** Starts adding documents from the given `id` to the Marks the FileGroup as <tt>completed</tt> and kicks off a
    * DocumentSetCreationJob.
    *
    * Does <em>not</em> create a DocumentSet.
    */
  def startClusteringExistingDocumentSet(id: Long) = AuthorizedAction(userOwningDocumentSet(id)).async { request =>
    MassUploadControllerForm.edit.bindFromRequest()(request).fold(
      e => Future(BadRequest),
      values => {
        val (lang, splitDocuments, metadataJson) = values

        fileGroupBackend.find(request.user.email, None).flatMap(_ match {
          case None => Future.successful(NotFound)
          case Some(fileGroup) => {
            for {
              newFileGroup <- fileGroupBackend.addToDocumentSet(
                fileGroup.id,
                id,
                lang,
                splitDocuments,
                metadataJson
              ).map(_.get)
            } yield {
              jobQueueSender.send(DocumentSetCommands.AddDocumentsFromFileGroup(newFileGroup))
              Redirect(routes.DocumentSetController.show(id))
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
  def cancel = AuthorizedAction(anyUser).async { request =>
    fileGroupBackend.find(request.user.email, None)
      .flatMap(_ match {
        case Some(fileGroup) => fileGroupBackend.destroy(fileGroup.id)
        case None => Future.successful(())
      })
      .map(_ => Accepted)
  }

  private def findUploadInCurrentFileGroup(userEmail: String, guid: UUID): Future[Option[GroupedFileUpload]] = {
    fileGroupBackend.find(userEmail, None)
      .flatMap(_ match {
        case None => Future.successful(None)
        case Some(fileGroup) => groupedFileUploadBackend.find(fileGroup.id, guid)
      })
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {
  override protected val documentSetBackend = DocumentSetBackend
  override protected val fileGroupBackend = FileGroupBackend
  override protected val jobQueueSender = JobQueueSender
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
  override protected val uploadIterateeFactory = GroupedFileUploadIteratee.apply _
}
