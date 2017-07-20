package controllers

import akka.util.ByteString
import com.google.inject.ImplementedBy
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.util.UUID
import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.streams.Accumulator
import play.api.mvc.{ Action, BodyParser, Request, RequestHeader, Result }
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{CsvImport,DocumentSet,Upload,UploadedFile}
import com.overviewdocs.models.tables.{CsvImports,Uploads,UploadedFiles}
import controllers.auth.Authorities.anyUser
import controllers.backend.DocumentSetBackend
import controllers.forms.UploadControllerForm
import controllers.util.{FileUploadIteratee,JobQueueSender}
import models.upload.OverviewUpload
import models.User

/** Handles a file upload, storing the file in a LargeObject.
  * 
  * Most of the work related to the upload happens in FileUploadIteratee.
  */
class UploadController @Inject() (
  documentSetBackend: DocumentSetBackend,
  components: UploadController.Components,
  val controllerComponents: ControllerComponents
) extends BaseController {
  /** Take bytes from the user and feed them into an Upload. */
  def create(guid: UUID) = Action[OverviewUpload](authorizedFileUploadBodyParser(guid)) { request =>
    if (isUploadComplete(request.body)) {
      Ok
    } else {
      BadRequest
    }
  }

  /** Turn an Upload into a DocumentSet and CsvImport. */
  def startClustering(guid: UUID) = authorizedAction(anyUser).async { request =>
    UploadControllerForm().bindFromRequest()(request).fold(
      form => Future.successful(BadRequest),
      { data =>
        val lang: String = data

        components.findUpload(request.user.id, guid) match {
          case None => Future.successful(NotFound)
          case Some(upload) => {
            if (!isUploadComplete(upload)) {
              Future.successful(Conflict)
            } else {
              val attributes = DocumentSet.CreateAttributes(title=upload.uploadedFile.filename)

              for {
                documentSet <- documentSetBackend.create(attributes, request.user.email)
                _ <- components.createCsvImport(documentSet, upload.underlying, upload.uploadedFile.underlying, lang)
              } yield Redirect(routes.DocumentSetController.show(documentSet.id))
            }
          }
        }
      })
  }

  private def isUploadComplete(upload: OverviewUpload) = upload.uploadedFile.size == upload.size

  def show(guid: UUID) = authorizedAction(anyUser) { implicit request =>
    def contentRange(upload: OverviewUpload): String = "bytes 0-%d/%d".format(upload.uploadedFile.size - 1, upload.size)
    def contentDisposition(upload: OverviewUpload): String = upload.uploadedFile.contentDisposition

    components.findUpload(request.user.id, guid).map { u =>
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
  def authorizedFileUploadBodyParser(guid: UUID) = authorizedBodyParser(anyUser) { user =>
    fileUploadBodyParser(user, guid)
  }

  def fileUploadBodyParser(user: User, guid: UUID): BodyParser[OverviewUpload] = BodyParser("File upload") { request =>
    components.fileUploadAccumulator(user.id, guid, request)
  }
}

object UploadController {
  @ImplementedBy(classOf[UploadController.BlockingDatabaseComponents])
  trait Components {
    def fileUploadAccumulator(userId: Long, guid: UUID, requestHeader: RequestHeader): Accumulator[ByteString, Either[Result, OverviewUpload]]

    def findUpload(userId: Long, guid: UUID): Option[OverviewUpload]

    /** Creates a CsvImport in the database, notifies the worker, and removes the
      * UploadedFile that created it.
      */
    def createCsvImport(
      documentSet: DocumentSet,
      upload: Upload,
      uploadedfile: UploadedFile,
      lang: String
    ): Future[Unit]
  }

  class BlockingDatabaseComponents @Inject() (jobQueueSender: JobQueueSender) extends Components with HasDatabase {
    override def fileUploadAccumulator(userId: Long, guid: UUID, requestHeader: RequestHeader): Accumulator[ByteString, Either[Result, OverviewUpload]] = {
      FileUploadIteratee.store(userId, guid, requestHeader)
    }

    override def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = {
      OverviewUpload.find(userId, guid)
    }

    override def createCsvImport(
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
          charsetName=uploadedFile.maybeCharset.map(_.name).getOrElse("utf-8"),
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
        jobQueueSender.send(DocumentSetCommands.AddDocumentsFromCsvImport(csvImport))
      }
    }
  }
}
 
