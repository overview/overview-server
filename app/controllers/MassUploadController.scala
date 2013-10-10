package controllers

import java.util.UUID
import play.api.mvc.{ Controller, Request, RequestHeader, Result }
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.util.TransactionAction
import controllers.auth.Authorities.anyUser
import play.api.mvc.BodyParser
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.auth.AuthorizedBodyParser
import controllers.auth.AuthorizedAction
import controllers.util.MassUploadFileIteratee
import org.overviewproject.tree.orm.FileGroup
import models.orm.finders.FileGroupFinder
import models.orm.finders.GroupedFileUploadFinder
import org.overviewproject.jobs.models.ProcessGroupedFileUpload
import controllers.util.JobQueueSender
import org.overviewproject.tree.orm.GroupedFileUpload
import controllers.forms.MassUploadControllerForm
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob
import models.orm.stores.DocumentSetStore
import models.orm.stores.DocumentSetUserStore
import org.overviewproject.tree.orm.DocumentSetUser
import org.overviewproject.tree.Ownership
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.jobs.models.StartClustering

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
    def createMassUploadDocumentSetCreationJob(documentSetId: Long, lang: String, suppliedStopWords: String): DocumentSetCreationJob

  }

  trait MessageQueue {
    /** Notify worker that an uploaded file needs to be processed */
    def sendProcessFile(fileGroupId: Long, groupedFileUploadId: Long): Either[Unit, Unit]

    /** Notify the worker that clustering can start */
    def startClustering(fileGroupId: Long, title: String, lang: String, suppliedStopWords: String): Either[Unit, Unit]
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

  private def showRequestHeaders(upload: GroupedFileUpload): Seq[(String, String)] = Seq(
    (CONTENT_LENGTH, s"${upload.uploadedSize}"),
    (CONTENT_RANGE, s"0-${upload.uploadedSize - 1}/${upload.size}"),
    (CONTENT_DISPOSITION, s"attachment ; filename=${upload.name}"))

  private def startClusteringFileGroupWithOptions(userEmail: String, options: (String, String, Option[String])): Result = {
    storage.findCurrentFileGroup(userEmail) match {
      case Some(fileGroup) => {
        val (name, lang, optionalStopWords) = options
        val suppliedStopWords = optionalStopWords.getOrElse("")

        val documentSet = storage.createDocumentSet(userEmail, name, lang, suppliedStopWords)
        storage.createMassUploadDocumentSetCreationJob(documentSet.id, lang, suppliedStopWords)
        messageQueue.startClustering(fileGroup.id, name, lang, suppliedStopWords)

        Ok
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
    import org.overviewproject.tree.orm.FileJobState.InProgress
    import org.overviewproject.tree.orm.DocumentSetCreationJobState.Preparing
    import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload

    override def findCurrentFileGroup(userEmail: String): Option[FileGroup] =
      FileGroupFinder.byUserAndState(userEmail, InProgress).headOption

    override def findGroupedFileUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload] =
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroupId, guid).headOption

    override def createDocumentSet(userEmail: String, title: String, lang: String, suppliedStopWords: String): DocumentSet = {
      val documentSet = DocumentSetStore.insertOrUpdate(
        DocumentSet(title = title, lang = lang, suppliedStopWords = suppliedStopWords))

      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, userEmail, Ownership.Owner))

      documentSet
    }

    override def createMassUploadDocumentSetCreationJob(documentSetId: Long, lang: String, suppliedStopWords: String): DocumentSetCreationJob = {
      DocumentSetCreationJobStore.insertOrUpdate(
        DocumentSetCreationJob(
          documentSetId = documentSetId,
          lang = lang,
          suppliedStopWords = suppliedStopWords,
          state = Preparing,
          jobType = FileUpload))
    }

  }

  class ApolloQueue extends MessageQueue {

    override def sendProcessFile(fileGroupId: Long, groupedFileUploadId: Long): Either[Unit, Unit] = {
      val command = ProcessGroupedFileUpload(fileGroupId, groupedFileUploadId)
      JobQueueSender.send(command)
    }

    override def startClustering(fileGroupId: Long, title: String, lang: String, suppliedStopWords: String): Either[Unit, Unit] = {
      val command = StartClustering(fileGroupId, title, lang, suppliedStopWords)
      
      JobQueueSender.send(command)
    }
  }
}



