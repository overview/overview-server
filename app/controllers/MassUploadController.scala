package controllers

import java.util.UUID
import scala.concurrent.duration.{Duration, MILLISECONDS}
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ BodyParser, Controller, Request, RequestHeader, SimpleResult }
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.FileJobState._
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser }
import controllers.auth.Authorities.anyUser
import controllers.forms.MassUploadControllerForm
import controllers.util.{ JobQueueSender, MassUploadFileIteratee, TransactionAction }
import models.orm.finders.{ FileGroupFinder, GroupedFileUploadFinder }
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore, FileGroupStore }
import models.orm.stores.GroupedFileUploadStore

trait MassUploadController extends Controller {

  /**
   *  Upload a file in the current FileGroup. A `MassUploadFileIteratee` handles all
   *  the details of the upload. `create` notifies the worker that an upload needs to
   *  be processed.
   */
  def create(guid: UUID) = TransactionAction(authorizedUploadBodyParser(guid)) { implicit request: Request[GroupedFileUpload] =>
    val upload: GroupedFileUpload = request.body

    if (isUploadComplete(upload)) Ok
    else uploadRequestFailed(request)
  }

  /**
   * @returns information about the upload specified by `guid` in the headers of the response.
   * content_range and content_length are provided.
   */
  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>
    def resultWithHeaders(status: Status, upload: GroupedFileUpload): SimpleResult =
      status.withHeaders(showRequestHeaders(upload): _*)

    def resultWithContentDisposition(status: SimpleResult, upload: GroupedFileUpload): SimpleResult =
      status.withHeaders((CONTENT_DISPOSITION, upload.contentDisposition))

    findUploadInCurrentFileGroup(request.user.email, guid) match {
      case Some(u) if (isUploadComplete(u)) => resultWithHeaders(Ok, u)
      case Some(u) if (isUploadEmpty(u)) => resultWithContentDisposition(NoContent, u)
      case Some(u) => resultWithHeaders(PartialContent, u)
      case None => NotFound
    }
  }

  /**
   * Notify the worker that clustering can start as soon as all currently uploaded files
   * have been processed
   */
  def startClustering = AuthorizedAction(anyUser) { implicit request =>
    MassUploadControllerForm().bindFromRequest.fold(
      e => BadRequest,
      startClusteringFileGroupWithOptions(request.user.email, _))
  }

  /**
   * Cancel the upload and notify the worker to delete all uploaded files
   */
  def cancelUpload = AuthorizedAction(anyUser) { implicit request =>
    storage.deleteFileGroupByUser(request.user.email)
    Ok
  }

  // method to create the MassUploadFileIteratee
  protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[SimpleResult, GroupedFileUpload]]

  /** interface to database related methods */
  val storage: Storage

  /** interface to message queue related methods */
  val messageQueue: MessageQueue

  trait Storage {
    /** @returns a `FileGroup` `InProgress`, owned by the user, if one exists. */
    def findCurrentFileGroup(userEmail: String): Option[FileGroup]

    /** @returns a `GroupedFileUpload` with the specified `guid` if one exists in the specified `FileGroup` */
    def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload]

    /** @returns a newly created DocumentSet */
    def createDocumentSet(userEmail: String, title: String, lang: String): DocumentSet

    /** @returns a newly created DocumentSetCreationJob */
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, splitDocuments: Boolean,
                                               suppliedStopWords: String, importantWords: String): DocumentSetCreationJob

    /** @returns a FileGroup with state set to Complete */
    def completeFileGroup(fileGroup: FileGroup): FileGroup

    /** deletes the `FileGroup` owned by the user and associated data */
    def deleteFileGroupByUser(userEmail: String): Unit
  }

  trait MessageQueue {

    /** Notify the worker that clustering can start */
    def startClustering(documentSetId: Long, fileGroupId: Long, title: String, lang: String,
                        splitDocuments: Boolean, suppliedStopWords: String, importantWords: String): Unit
  }

  private def authorizedUploadBodyParser(guid: UUID) =
    AuthorizedBodyParser(anyUser) { user => uploadBodyParser(user.email, guid) }

  private def uploadBodyParser(userEmail: String, guid: UUID) =
    BodyParser("Mass upload bodyparser") { request =>
      massUploadFileIteratee(userEmail, request, guid)
    }

  private def findUploadInCurrentFileGroup(userEmail: String, guid: UUID): Option[GroupedFileUpload] =
    for {
      fileGroup <- storage.findCurrentFileGroup(userEmail)
      upload <- storage.findGroupedFileUpload(fileGroup.id, guid)
    } yield upload

  private def isUploadComplete(upload: GroupedFileUpload): Boolean =
    (upload.uploadedSize == upload.size) && (upload.size > 0)

  private def isUploadEmpty(upload: GroupedFileUpload): Boolean =
    upload.uploadedSize == 0

  private def showRequestHeaders(upload: GroupedFileUpload): Seq[(String, String)] = {
    def computeEnd(uploadedSize: Long): Long =
      if (upload.uploadedSize == 0) 0
      else upload.uploadedSize - 1

    Seq(
      (CONTENT_LENGTH, s"${upload.uploadedSize}"),
      (CONTENT_RANGE, s"0-${computeEnd(upload.uploadedSize)}/${upload.size}"),
      (CONTENT_DISPOSITION, upload.contentDisposition))
  }

  private def startClusteringFileGroupWithOptions(userEmail: String,
                                                  options: (String, String, Boolean, String, String)): SimpleResult = {
    storage.findCurrentFileGroup(userEmail) match {
      case Some(fileGroup) => {
        storage.completeFileGroup(fileGroup)

        val (name, lang, splitDocuments, suppliedStopWords, importantWords) = options
        val documentSet = storage.createDocumentSet(userEmail, name, lang)
        storage.createMassUploadDocumentSetCreationJob(
          documentSet.id, fileGroup.id, lang, splitDocuments, suppliedStopWords, importantWords)

        sendAsynchronousStartClusteringMessage(documentSet.id, fileGroup.id, name, lang, splitDocuments, suppliedStopWords, importantWords)

        Redirect(routes.DocumentSetController.index())
      }
      case None => NotFound
    }
  }

  private def uploadRequestFailed(request: Request[GroupedFileUpload]): SimpleResult = {
    val upload = request.body
    Logger.info(s"File Upload Bad Request ${upload.id}: ${upload.guid}\n${request.headers}")

    BadRequest
  }

  private def sendAsynchronousStartClusteringMessage(documentSetId: Long, fileGroupId: Long, name: String, lang: String,
                                                     splitDocuments: Boolean, suppliedStopWords: String,
                                                     importantWords: String) = {
    import play.api.Play.current
    import play.api.libs.concurrent.Execution.Implicits._
    /*
     * We want to send the message _after_ we've committed the job to the
     * database. This is the easiest way.
     *
     * FIXME make the _correct_ way easier than this stupid way.
     */
    val MessageSendDelay = Duration(10, MILLISECONDS)
    Akka.system.scheduler.scheduleOnce(MessageSendDelay) {
      messageQueue.startClustering(documentSetId, fileGroupId, name, lang, splitDocuments, suppliedStopWords, importantWords)
    }

  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {

  override protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[SimpleResult, GroupedFileUpload]] =
    MassUploadFileIteratee(userEmail, request, guid)

  override val storage = new DatabaseStorage
  override val messageQueue = new ApolloQueue

  class DatabaseStorage extends Storage {
    import org.overviewproject.tree.orm.FileJobState._
    import org.overviewproject.tree.orm.DocumentSetCreationJobState.FilesUploaded
    import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload

    override def findCurrentFileGroup(userEmail: String): Option[FileGroup] =
      FileGroupFinder.byUserAndState(userEmail, InProgress).headOption

    override def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload] =
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroupId, guid).headOption

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

    override def completeFileGroup(fileGroup: FileGroup): FileGroup =
      FileGroupStore.insertOrUpdate(fileGroup.copy(state = Complete))

    override def deleteFileGroupByUser(userEmail: String): Unit = {
      import org.overviewproject.postgres.SquerylEntrypoint._
      
      findCurrentFileGroup(userEmail).map { fileGroup =>
        GroupedFileUploadStore.deleteLargeObjectsInFileGroup(fileGroup.id)
        GroupedFileUploadStore.deleteByFileGroup(fileGroup.id)
        FileGroupStore.delete(fileGroup.id)
      }
    }
  }

  class ApolloQueue extends MessageQueue {

    override def startClustering(documentSetId: Long, fileGroupId: Long, title: String, lang: String,
                                 splitDocuments: Boolean, suppliedStopWords: String, importantWords: String): Unit = {
      val command = ClusterFileGroup(documentSetId, fileGroupId, title, lang, splitDocuments, suppliedStopWords, importantWords)

      if (JobQueueSender.send(command).isLeft)
        throw new Exception(s"Could not send StartClustering($fileGroupId, $title, $lang, $splitDocuments, $suppliedStopWords, $importantWords)")
    }
  }
}



