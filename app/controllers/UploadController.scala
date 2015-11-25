package controllers

import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.UUID
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader, Result }
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{CsvImport,DocumentSet,Upload,UploadedFile}
import com.overviewdocs.models.tables.{CsvImports,Uploads,UploadedFiles}
import controllers.auth.Authorities.anyUser
import controllers.auth.{AuthorizedAction,AuthorizedBodyParser,Authority,SessionFactory}
import controllers.backend.DocumentSetBackend
import controllers.forms.UploadControllerForm
import controllers.util.{FileUploadIteratee,JobQueueSender}
import models.upload.OverviewUpload
import models.User

/** Handles a file upload, storing the file in a LargeObject.
  * 
  * Most of the work related to the upload happens in FileUploadIteratee.
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

  /** Turn an Upload into a DocumentSet and CsvImport. */
  def startClustering(guid: UUID) = AuthorizedAction(anyUser).async { request =>
    UploadControllerForm().bindFromRequest()(request).fold(
      form => Future.successful(BadRequest),
      { data =>
        val lang: String = data

        findUpload(request.user.id, guid) match {
          case None => Future.successful(NotFound)
          case Some(upload) => {
            if (!isUploadComplete(upload)) {
              Future.successful(Conflict)
            } else {
              val attributes = DocumentSet.CreateAttributes(title=upload.uploadedFile.filename)

              for {
                documentSet <- documentSetBackend.create(attributes, request.user.email)
                _ <- createCsvImport(documentSet, upload.underlying, upload.uploadedFile.underlying, lang)
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

  /** Creates a CsvImport in the database, notifies the worker, and removes the
    * UploadedFile that created it.
    */
  protected def createCsvImport(
    documentSet: DocumentSet,
    upload: Upload,
    uploadedfile: UploadedFile,
    lang: String
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

  override protected def createCsvImport(
    documentSet: DocumentSet,
    upload: Upload,
    uploadedFile: UploadedFile,
    lang: String
  ) = {
    import database.api._

    val inserter = CsvImports
      .map(_.createAttributes)
      .returning(CsvImports)

    val transaction = (for {
      csvImport <- inserter.+=(CsvImport.CreateAttributes(
        documentSetId=documentSet.id,
        filename=uploadedFile.filename,
        charset=uploadedFile.maybeCharset.getOrElse(StandardCharsets.UTF_8),
        lang=lang,
        loid=upload.contentsOid,
        nBytes=upload.totalSize
      ))
      _ <- Uploads.filter(_.id === upload.id).delete
      _ <- UploadedFiles.filter(_.id === uploadedFile.id).delete
    } yield csvImport).transactionally

    for {
      csvImport <- database.run(transaction)
    } yield {
      JobQueueSender.send(DocumentSetCommands.AddDocumentsFromCsvImport(csvImport))
    }
  }
}
 
