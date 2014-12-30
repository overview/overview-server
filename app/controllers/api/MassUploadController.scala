package controllers.api

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialAction,RequestHeader,Result}
import scala.concurrent.Future

import controllers.auth.{ApiAuthorizedAction,ApiTokenFactory}
import controllers.auth.Authorities.anyUser
import controllers.backend.{ FileGroupBackend, GroupedFileUploadBackend }
import controllers.forms.MassUploadControllerForm
import controllers.iteratees.GroupedFileUploadIteratee
import controllers.util.JobQueueSender
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.OverviewDatabase
import org.overviewproject.models.{ApiToken,FileGroup,GroupedFileUpload}
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,DocumentSetUser}
import org.overviewproject.tree.Ownership
import org.overviewproject.util.ContentDisposition

trait MassUploadController extends ApiController {
  protected val fileGroupBackend: FileGroupBackend
  protected val groupedFileUploadBackend: GroupedFileUploadBackend
  protected val storage: MassUploadController.Storage
  protected val messageQueue: MassUploadController.MessageQueue
  protected val apiTokenFactory: ApiTokenFactory
  protected val uploadIterateeFactory: (GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit]

  /** Starts or resumes a file upload.  */
  def create(guid: UUID) = EssentialAction { request =>
    def badRequest(message: String): Iteratee[Array[Byte],Result] = {
        Iteratee.ignore.map(_ => BadRequest(jsonError(message)))
    }

    def findOrCreateFileGroup(apiToken: ApiToken): Future[FileGroup] = {
      val attributes = FileGroup.CreateAttributes(apiToken.createdBy, Some(apiToken.token))
      fileGroupBackend.findOrCreate(attributes)
    }

    def findOrCreateGroupedFileUpload(fileGroup: FileGroup, info: MassUploadController.RequestInfo): Future[GroupedFileUpload] = {
      val attributes = GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        info.contentType,
        info.filename,
        info.total
      )
      groupedFileUploadBackend.findOrCreate(attributes)
    }

    def createIteratee(upload: GroupedFileUpload, info: MassUploadController.RequestInfo): Iteratee[Array[Byte],Result] = {
      if (info.start > upload.uploadedSize) {
        badRequest(s"Tried to resume past last uploaded byte. Resumed at byte ${info.start}, but only ${upload.uploadedSize} bytes have been uploaded.")
      } else {
        uploadIterateeFactory(upload, info.start).map(_ => Created)
      }
    }

    val futureApiToken: Future[Either[Result,ApiToken]] = apiTokenFactory.loadAuthorizedApiToken(request, anyUser)
    val futureIteratee: Future[Iteratee[Array[Byte],Result]] = futureApiToken.flatMap(_ match {
      case Left(result) => Future.successful(Iteratee.ignore.map(_ => result))
      case Right(apiToken) => {
        MassUploadController.RequestInfo.fromRequest(request) match {
          case Some(info) => {
            for {
              fileGroup <- findOrCreateFileGroup(apiToken)
              groupedFileUpload <- findOrCreateGroupedFileUpload(fileGroup, info)
            } yield createIteratee(groupedFileUpload, info)
          }
          case None => Future.successful(badRequest(("Request must have Content-Range or Content-Length header")))
        }
      }
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
    */
  def startClustering = ApiAuthorizedAction(anyUser).async { request =>
    MassUploadControllerForm().bindFromRequest()(request).fold(
      e => Future(BadRequest),
      startClusteringFileGroupWithOptions(request.apiToken.createdBy, request.apiToken.token, _)
    )
  }

  /** Cancels the upload and notify the worker to delete all uploaded files
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

  private def startClusteringFileGroupWithOptions(userEmail: String, apiToken: String,
                                                  options: (String, String, Boolean, String, String)): Future[Result] = {
    val (name, lang, splitDocuments, suppliedStopWords, importantWords) = options

    fileGroupBackend.find(userEmail, Some(apiToken)).flatMap(_ match {
      case Some(fileGroup) => {
        val job: DocumentSetCreationJob = /*OverviewDatabase.inTransaction*/ {
          val documentSet = storage.createDocumentSet(userEmail, name)
          storage.createMassUploadDocumentSetCreationJob(
            documentSet.id, fileGroup.id, lang, splitDocuments, suppliedStopWords, importantWords)
        }

        fileGroupBackend.update(fileGroup.id, true) // TODO put in transaction
          .map(_ => messageQueue.startClustering(job, name))
          .map(_ => Created)
      }
      case None => Future.successful(NotFound)
    })
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {
  override protected val storage = DatabaseStorage
  override protected val messageQueue = ApolloQueue
  override protected val fileGroupBackend = FileGroupBackend
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
  override protected val apiTokenFactory = ApiTokenFactory
  override protected val uploadIterateeFactory = GroupedFileUploadIteratee.apply _

  trait Storage {
    /** @returns a newly created DocumentSet */
    def createDocumentSet(userEmail: String, title: String): DocumentSet

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

    override def createDocumentSet(userEmail: String, title: String): DocumentSet = {
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

  private case class RequestInfo(filename: String, contentType: String, start: Long, total: Long)
  
  private object RequestInfo {
    def fromRequest(request: RequestHeader): Option[RequestInfo] = {
      val contentType = request.headers.get(CONTENT_TYPE).getOrElse("")
      val contentDisposition = request.headers.get(CONTENT_DISPOSITION)
      val filename: String = contentDisposition.flatMap(ContentDisposition(_).filename).getOrElse("")

      def one(start: Long, total: Long) = RequestInfo(filename, contentType, start, total)

      // A string matching "(\d{0,18})" cannot throw an exception when converted to Long.
      val rangeResults = request.headers.get(CONTENT_RANGE).flatMap { contentRanges =>
        """^bytes (\d{0,18})-\d+/(\d{0,18})$""".r.findFirstMatchIn(contentRanges).map { rangeMatch =>
          val List(start, total) = rangeMatch.subgroups.take(2)

          one(start.toLong, total.toLong)
        }
      }
      
      val lengthResults = request.headers.get(CONTENT_LENGTH).flatMap { contentLengths =>
        """^(\d{0,18})$""".r.findFirstMatchIn(contentLengths).map { lengthMatch =>
          val List(total) = lengthMatch.subgroups.take(1)
          one(0L, total.toLong)
        }
      }

      (rangeResults ++ lengthResults).headOption
    }
  }
}
