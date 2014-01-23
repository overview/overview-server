package controllers

import java.util.UUID
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ BodyParser, Controller, Request, RequestHeader, Result }
import org.overviewproject.jobs.models.{ CancelUpload, ProcessGroupedFileUpload, StartClustering }
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.FileJobState._
import controllers.auth.Authorities.anyUser
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser }
import controllers.forms.MassUploadControllerForm
import controllers.util.{ JobQueueSender, MassUploadFileIteratee, TransactionAction }
import models.orm.finders.{ FileGroupFinder, GroupedFileUploadFinder }
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.orm.stores.FileGroupStore


trait MassUploadController extends Controller {

  /**
   *  Upload a file in the current FileGroup. A `MassUploadFileIteratee` handles all
   *  the details of the upload. `create` notifies the worker that an upload needs to
   *  be processed.
   */
  def create(guid: UUID) = TransactionAction(authorizedUploadBodyParser(guid)) { implicit request: Request[GroupedFileUpload] =>
    val upload: GroupedFileUpload = request.body

    if (isUploadComplete(upload)) {
      messageQueue.sendProcessFile(upload.fileGroupId, upload.id)
      Ok
    } else BadRequest
  }

  /**
   * @returns information about the upload specified by `guid` in the headers of the response.
   * content_range and content_length are provided.
   */
  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>
    def resultWithHeaders(status: Status, upload: GroupedFileUpload): Result =
      status.withHeaders(showRequestHeaders(upload): _*)

    findUploadInCurrentFileGroup(request.user.email, guid) match {
      case Some(u) if (isUploadComplete(u)) => resultWithHeaders(Ok, u)
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
    storage.findCurrentFileGroup(request.user.email).map { fileGroup =>
      messageQueue.cancelUpload(fileGroup.id)
      
      Ok
    }.getOrElse(NotFound)  
  }

  // method to create the MassUploadFileIteratee
  protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]]

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
    def createDocumentSet(userEmail: String, title: String, lang: String, suppliedStopWords: String): DocumentSet

    /** @returns a newly created DocumentSetCreationJob */
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, suppliedStopWords: String): DocumentSetCreationJob

    /** @returns a FileGroup with state set to Complete */
    def completeFileGroup(fileGroup: FileGroup): FileGroup
  }

  trait MessageQueue {
    /** Notify worker that an uploaded file needs to be processed */
    def sendProcessFile(fileGroupId: Long, groupedFileUploadId: Long): Unit

    /** Notify the worker that clustering can start */
    def startClustering(fileGroupId: Long, title: String, lang: String, suppliedStopWords: String): Unit
    
    /** Tell worker to delete all processing for the FileGroup and delete all associated files */
    def cancelUpload(fileGroupId: Long): Unit
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

  private def showRequestHeaders(upload: GroupedFileUpload): Seq[(String, String)] = {
    def computeEnd(uploadedSize: Long): Long =
      if (upload.uploadedSize == 0) 0
      else upload.uploadedSize - 1

    Seq(
      (CONTENT_LENGTH, s"${upload.uploadedSize}"),
      (CONTENT_RANGE, s"0-${computeEnd(upload.uploadedSize)}/${upload.size}"),
      (CONTENT_DISPOSITION, upload.contentDisposition)
    )
  }

  private def startClusteringFileGroupWithOptions(userEmail: String, options: (String, String, Option[String])): Result = {
    storage.findCurrentFileGroup(userEmail) match {
      case Some(fileGroup) => {
        val (name, lang, optionalStopWords) = options
        val suppliedStopWords = optionalStopWords.getOrElse("")

        storage.completeFileGroup(fileGroup)
        
        val documentSet = storage.createDocumentSet(userEmail, name, lang, suppliedStopWords)
        storage.createMassUploadDocumentSetCreationJob(documentSet.id, fileGroup.id, lang, suppliedStopWords)
        messageQueue.startClustering(fileGroup.id, name, lang, suppliedStopWords)

        Redirect(routes.DocumentSetController.index())
      }
      case None => NotFound
    }
  }
}

/** Controller implementation */
object MassUploadController extends MassUploadController {

  override protected def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
    MassUploadFileIteratee(userEmail, request, guid)

  override val storage = new DatabaseStorage
  override val messageQueue = new ApolloQueue

  class DatabaseStorage extends Storage {
    import org.overviewproject.tree.orm.FileJobState._
    import org.overviewproject.tree.orm.DocumentSetCreationJobState.Preparing
    import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload

    override def findCurrentFileGroup(userEmail: String): Option[FileGroup] =
      FileGroupFinder.byUserAndState(userEmail, InProgress).headOption

    override def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload] =
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroupId, guid).headOption

    override def createDocumentSet(userEmail: String, title: String, lang: String, suppliedStopWords: String): DocumentSet = {
      val documentSet = DocumentSetStore.insertOrUpdate(
        DocumentSet(title = title))

      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, userEmail, Ownership.Owner))

      documentSet
    }

    override def createMassUploadDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, lang: String, suppliedStopWords: String): DocumentSetCreationJob = {
      DocumentSetCreationJobStore.insertOrUpdate(
        DocumentSetCreationJob(
          documentSetId = documentSetId,
          fileGroupId = Some(fileGroupId),
          lang = lang,
          suppliedStopWords = suppliedStopWords,
          state = Preparing,
          jobType = FileUpload))
    }

    override def completeFileGroup(fileGroup: FileGroup): FileGroup = 
      FileGroupStore.insertOrUpdate(fileGroup.copy(state = Complete))
  }

  class ApolloQueue extends MessageQueue {

    override def sendProcessFile(fileGroupId: Long, groupedFileUploadId: Long): Unit = {
      val command = ProcessGroupedFileUpload(fileGroupId, groupedFileUploadId)
      if (JobQueueSender.send(command).isLeft) 
        throw new Exception(s"Could not send ProcessFile($fileGroupId, $groupedFileUploadId)")
    }

    override def startClustering(fileGroupId: Long, title: String, lang: String, suppliedStopWords: String): Unit = {
      val command = StartClustering(fileGroupId, title, lang, suppliedStopWords)

      if (JobQueueSender.send(command).isLeft) 
        throw new Exception(s"Could not send StartClustering($fileGroupId, $title, $lang, $suppliedStopWords)")
    }
    
    override def cancelUpload(fileGroupId: Long): Unit = {
      val command = CancelUpload(fileGroupId)
      
      if (JobQueueSender.send(command).isLeft)
        throw new Exception(s"Cound not send CancelUpload($fileGroupId)")
    }
  }
}



