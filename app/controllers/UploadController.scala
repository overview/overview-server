package controllers

import java.util.UUID
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ BodyParser, Controller, Request, RequestHeader, Result }
import scala.concurrent.Future

import org.overviewproject.tree.{ DocumentSetCreationJobType, Ownership }
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, DocumentSetUser }
import controllers.auth.Authorities.anyUser
import controllers.auth.{ AuthorizedAction, AuthorizedBodyParser, Authority, SessionFactory }
import controllers.forms.UploadControllerForm
import controllers.util.{ FileUploadIteratee, PgConnection, TransactionAction }
import models.orm.{Session, User}
import models.orm.finders.UserFinder
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.OverviewDatabase
import models.upload.OverviewUpload

/**
 * Handles a file upload, storing the file in a LargeObject, updating the upload table,
 * and starting a DocumentSetCreationJob. Most of the work related to the upload happens
 * in FileUploadIteratee.
 */
trait UploadController extends Controller {
  /**
   * Handle file upload.
   */
  def create(guid: UUID) = TransactionAction(authorizedFileUploadBodyParser(guid)) { implicit request: Request[OverviewUpload] =>
    val upload: OverviewUpload = request.body

    if (isUploadComplete(upload)) {
      Ok
    } else {
      BadRequest
    }
  }

  def startClustering(guid: UUID) = AuthorizedAction.inTransaction(anyUser) { implicit request =>

    UploadControllerForm().bindFromRequest().fold(
      f => BadRequest,
      { f =>
        val lang = f._1
        val stopWords = f._2.getOrElse("")
        val importantWords = f._3.getOrElse("")
        
        findUpload(request.user.id, guid) match {
          case Some(u) => {
            if (isUploadComplete(u)) {
              createDocumentSetCreationJob(u, lang, stopWords, importantWords)
              deleteUpload(u)
              Redirect(routes.DocumentSetController.index())
            } else {
              Conflict
            }
          }
          case None => NotFound
        }
      })
  }

  private def isUploadComplete(upload: OverviewUpload) = {
    upload.uploadedFile.size == upload.size && upload.size > 0
  }

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
  protected def deleteUpload(upload: OverviewUpload): Unit
  protected def createDocumentSetCreationJob(upload: OverviewUpload, documentSetLanguage: String, suppliedStopWords: String, importantWords: String): Unit
}

/**
 * UploadController implementation that uses FileUploadIteratee
 */
object UploadController extends UploadController with PgConnection {

  def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
    FileUploadIteratee.store(userId, guid, requestHeader)

  def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = OverviewUpload.find(userId, guid)

  def deleteUpload(upload: OverviewUpload) = withPgConnection { implicit c =>
    upload.delete
  }

  override protected def createDocumentSetCreationJob(upload: OverviewUpload, documentSetLanguage: String, suppliedStopWords: String, importantWords: String) {
    UserFinder.byId(upload.userId).headOption.map { u: User =>
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
        title = upload.uploadedFile.filename,
        uploadedFileId = Some(upload.uploadedFile.id)))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet.id, u.email, Ownership.Owner))
      DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
        documentSetId = documentSet.id,
        lang = documentSetLanguage,
        suppliedStopWords = suppliedStopWords,
        importantWords = importantWords,
        state = DocumentSetCreationJobState.NotStarted,
        jobType = DocumentSetCreationJobType.CsvUpload,
        contentsOid = Some(upload.contentsOid)))
    }
  }
}
 
