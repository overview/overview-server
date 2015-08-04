package controllers

import java.sql.Connection
import java.util.UUID
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader, Result }
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser, Authority, SessionFactory }
import controllers.backend.DocumentSetBackend
import controllers.forms.UploadControllerForm
import controllers.util.FileUploadIteratee
import models.orm.stores.DocumentSetCreationJobStore
import models.upload.OverviewUpload
import models.User
import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.models.DocumentSet
import com.overviewdocs.tree.{ DocumentSetCreationJobType, Ownership }
import com.overviewdocs.tree.orm.{ DocumentSetCreationJob, DocumentSetCreationJobState }

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

  def show(guid: UUID) = AuthorizedAction.inTransaction(anyUser) { implicit request =>
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
object UploadController extends UploadController {
  override protected val documentSetBackend = DocumentSetBackend

  override protected def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
    FileUploadIteratee.store(userId, guid, requestHeader)

  override protected def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = DeprecatedDatabase.inTransaction {
    OverviewUpload.find(userId, guid)
  }

  override protected def createJobAndDeleteUpload(
    documentSet: DocumentSet,
    user: User,
    upload: OverviewUpload,
    lang: String,
    suppliedStopWords: String,
    importantWords: String
  ) = Future(DeprecatedDatabase.inTransaction {
    DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
      documentSetId=documentSet.id,
      lang=lang,
      suppliedStopWords=suppliedStopWords,
      importantWords=importantWords,
      state=DocumentSetCreationJobState.NotStarted,
      jobType=DocumentSetCreationJobType.CsvUpload,
      contentsOid=Some(upload.contentsOid)
    ))

    upload.delete
  })
}
 
