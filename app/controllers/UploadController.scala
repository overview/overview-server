package controllers

import java.sql.Connection
import java.util.UUID
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader, Result }
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType}
import com.overviewdocs.models.tables.{DocumentSetCreationJobs,Uploads}
import controllers.auth.Authorities.anyUser
import controllers.auth.{AuthorizedAction, AuthorizedBodyParser,Authority,SessionFactory}
import controllers.backend.DocumentSetBackend
import controllers.forms.UploadControllerForm
import controllers.util.FileUploadIteratee
import models.upload.OverviewUpload
import models.User

/**
 * Handles a file upload, storing the file in a LargeObject, updating the upload table,
 * and starting a DocumentSetCreationJob. Most of the work related to the upload happens
 * in FileUploadIteratee.
 */
trait UploadController extends Controller {
  protected val documentSetBackend: DocumentSetBackend

  /** Take bytes from the user and feed them into an Upload. */
  def create(guid: UUID) = Action[OverviewUpload](authorizedFileUploadBodyParser(guid)) { request =>
    if (isUploadComplete(request.body)) {
      Ok
    } else {
      BadRequest
    }
  }

  /** Turn an Upload into a DocumentSet and DocumentSetCreationJob. */
  def startClustering(guid: UUID) = AuthorizedAction(anyUser).async { request =>
    UploadControllerForm().bindFromRequest()(request).fold(
      form => Future.successful(BadRequest),
      { form =>
        val lang = form._1
        val stopWords = form._2.getOrElse("")
        val importantWords = form._3.getOrElse("")

        findUpload(request.user.id, guid) match {
          case None => Future.successful(NotFound)
          case Some(upload) => {
            if (!isUploadComplete(upload)) {
              Future.successful(Conflict)
            } else {
              val attributes = DocumentSet.CreateAttributes(
                title=upload.uploadedFile.filename,
                uploadedFileId=Some(upload.uploadedFile.id)
              )

              for {
                documentSet <- documentSetBackend.create(attributes, request.user.email)
                _ <- createJobAndDeleteUpload(documentSet, request.user, upload, lang, stopWords, importantWords)
              } yield Redirect(routes.DocumentSetController.show(documentSet.id))
            }
          }
        }
      })
  }

  private def isUploadComplete(upload: OverviewUpload) = upload.uploadedFile.size == upload.size

  def show(guid: UUID) = AuthorizedAction(anyUser) { implicit request =>
    def contentRange(upload: OverviewUpload): String = "bytes 0-%d/%d".format(upload.uploadedFile.size - 1, upload.size)
    def contentDisposition(upload: OverviewUpload): String = upload.uploadedFile.contentDisposition

    findUpload(request.user.id, guid).map { u =>
      if (isUploadComplete(u)) {
        Ok.withHeaders(
          (CONTENT_LENGTH, u.uploadedFile.size.toString),
          (CONTENT_DISPOSITION, contentDisposition(u))
        )
      } else {
        PartialContent.withHeaders(
          (CONTENT_RANGE, contentRange(u)),
          (CONTENT_DISPOSITION, contentDisposition(u))
        )
      }
    }.getOrElse(NotFound)
  }

  /** Gets the guid and user info to the body parser handling the file upload */
  def authorizedFileUploadBodyParser(guid: UUID) = AuthorizedBodyParser(anyUser) { user => fileUploadBodyParser(user, guid) }

  def fileUploadBodyParser(user: User, guid: UUID): BodyParser[OverviewUpload] = BodyParser("File upload") { request =>
    fileUploadIteratee(user.id, guid, request)
  }

  protected def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]]
  protected def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]

  protected def createJobAndDeleteUpload(
    documentSet: DocumentSet,
    user: User,
    upload: OverviewUpload,
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ): Future[Unit]
}

/**
 * UploadController implementation that uses FileUploadIteratee
 */
object UploadController extends UploadController with HasDatabase {
  override protected val documentSetBackend = DocumentSetBackend

  override protected def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
    FileUploadIteratee.store(userId, guid, requestHeader)

  override protected def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = {
    OverviewUpload.find(userId, guid)
  }

  override protected def createJobAndDeleteUpload(
    documentSet: DocumentSet,
    user: User,
    upload: OverviewUpload,
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = {
    import database.api._

    val t = (for {
      _ <- DocumentSetCreationJobs.map(_.createAttributes).+=(DocumentSetCreationJob.CreateAttributes(
        documentSetId=documentSet.id,
        jobType=DocumentSetCreationJobType.CsvUpload,
        retryAttempts=0,
        lang=lang,
        suppliedStopWords=suppliedStopWords,
        importantWords=importantWords,
        splitDocuments=false,
        documentcloudUsername=None,
        documentcloudPassword=None,
        contentsOid=Some(upload.contentsOid),
        sourceDocumentSetId=None,
        treeTitle=None,
        treeDescription=None,
        tagId=None,
        state=DocumentSetCreationJobState.NotStarted,
        fractionComplete=0,
        statusDescription="",
        canBeCancelled=true
      ))
      _ <- Uploads.filter(_.id === upload.id).delete
    } yield ()).transactionally

    database.runUnit(t)
  }
}
 
