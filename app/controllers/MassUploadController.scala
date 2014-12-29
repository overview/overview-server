package controllers

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.Logger
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader, Result }
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser }
import controllers.backend.{ FileGroupBackend, GroupedFileUploadBackend }
import controllers.forms.MassUploadControllerForm
import controllers.util.{AuthorizedMassUploadBodyParser,JobQueueSender,MassUploadBodyParser,MassUploadFileIteratee}
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.OverviewDatabase
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,DocumentSetUser}
import org.overviewproject.tree.Ownership
import org.overviewproject.util.ContentDisposition

trait MassUploadController extends Controller {
  protected val fileGroupBackend: FileGroupBackend
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val storage: MassUploadController.Storage
  protected val messageQueue: MassUploadController.MessageQueue
  protected val uploadBodyParserFactory: Function1[UUID,BodyParser[Unit]]

  /** Starts or resumes a file upload.
    *
    * The BodyParser does the real work. This method merely says Ok.
    */
   def create(guid: UUID) = Action(uploadBodyParserFactory(guid)) { _ => Created }

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
    */
  def startClustering = AuthorizedAction(anyUser).async { request =>
    MassUploadControllerForm().bindFromRequest()(request).fold(
      e => Future(BadRequest),
      startClusteringFileGroupWithOptions(request.user.email, _)
    )
  }

  /** Cancels the upload and notify the worker to delete all uploaded files
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

  private def startClusteringFileGroupWithOptions(userEmail: String,
                                                  options: (String, String, Boolean, String, String)): Future[Result] = {
    val (name, lang, splitDocuments, suppliedStopWords, importantWords) = options

    fileGroupBackend.find(userEmail, None).flatMap(_ match {
      case Some(fileGroup) => {
        val job: DocumentSetCreationJob = OverviewDatabase.inTransaction {
          val documentSet = storage.createDocumentSet(userEmail, name, lang)
          storage.createMassUploadDocumentSetCreationJob(
            documentSet.id, fileGroup.id, lang, splitDocuments, suppliedStopWords, importantWords)
        }

        fileGroupBackend.update(fileGroup.id, true) // TODO put in transaction
          .map(_ => messageQueue.startClustering(job, name))
          .map(_ => Redirect(routes.DocumentSetController.index()))
      }
      case None => Future.successful(NotFound)
    })
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {
  override val storage = DatabaseStorage
  override val messageQueue = ApolloQueue
  override val fileGroupBackend = FileGroupBackend
  override val groupedFileUploadBackend = GroupedFileUploadBackend
  override val uploadBodyParserFactory = ((guid: UUID) => new AuthorizedMassUploadBodyParser(guid))

  trait Storage {
    /** @returns a newly created DocumentSet */
    def createDocumentSet(userEmail: String, title: String, lang: String): DocumentSet

    /** @returns a newly created DocumentSetCreationJob */
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, splitDocuments: Boolean,
                                               suppliedStopWords: String, importantWords: String): DocumentSetCreationJob
  }

  trait MessageQueue {
    /** Notify the worker that clustering can start */
    def startClustering(job: DocumentSetCreationJob, documentSetTitle: String): Future[Unit]
  }

  object DatabaseStorage extends Storage {
    import org.overviewproject.tree.orm.DocumentSetCreationJobState.FilesUploaded
    import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload

    override def createDocumentSet(userEmail: String, title: String, lang: String): DocumentSet = {
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(title = title))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, userEmail, Ownership.Owner))

      documentSet
    }

    override def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long,
                                                        lang: String, splitDocuments: Boolean,
                                                        suppliedStopWords: String,
                                                        importantWords: String): DocumentSetCreationJob = {
      DocumentSetCreationJobStore.insertOrUpdate(
        DocumentSetCreationJob(
          documentSetId = documentSetId,
          fileGroupId = Some(fileGroupId),
          lang = lang,
          splitDocuments = splitDocuments,
          suppliedStopWords = suppliedStopWords,
          importantWords = importantWords,
          state = FilesUploaded,
          jobType = FileUpload))
    }
  }

  object ApolloQueue extends MessageQueue {
    override def startClustering(job: DocumentSetCreationJob, documentSetTitle: String): Future[Unit] = {
      val command = ClusterFileGroup(
        documentSetId=job.documentSetId,
        fileGroupId=job.fileGroupId.get,
        name=documentSetTitle,
        lang=job.lang,
        splitDocuments=job.splitDocuments,
        stopWords=job.suppliedStopWords,
        importantWords=job.importantWords
      )

      JobQueueSender.send(command)
    }
  }
}
