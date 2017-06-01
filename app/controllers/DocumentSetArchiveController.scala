package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject
import play.api.http.HttpEntity
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Result
import scala.concurrent.Future

import com.overviewdocs.util.ContentDisposition
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userCanExportDocumentSet
import controllers.backend.{ArchiveEntryBackend,SelectionBackend}
import models.ArchiveEntry
import models.archive.{ArchiveFactory,ZipArchive}

class DocumentSetArchiveController @Inject() (
  val archiveEntryBackend: ArchiveEntryBackend,
  val archiveFactory: ArchiveFactory,
  val selectionBackend: SelectionBackend
) extends Controller with SelectionHelpers {

  def archive(documentSetId: Long, filename: String) = AuthorizedAction(userCanExportDocumentSet(documentSetId)).async { implicit request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        selection.getAllDocumentIds.flatMap(_ match {
          case documentIds if documentIds.length > 0xffff => Future.successful(flashWarning("tooManyEntries"))
          case documentIds => {
            for {
              archiveEntries <- archiveEntryBackend.showMany(documentSetId, documentIds)
            } yield {
              archiveFactory.createZip(documentSetId, archiveEntries) match {
                case Left(message) => flashWarning(message)
                case Right(archive) => streamArchive(archive, filename)
              }
            }
          }
        })
      }
    })
  }

  private def streamArchive(archive: ZipArchive, filename: String): Result = {
    val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition

    Ok.sendEntity(HttpEntity.Streamed(archive.stream, Some(archive.size), Some("application/zip")))
      .withHeaders(
        CONTENT_LENGTH -> archive.size.toString,
        CONTENT_TYPE -> "application/zip",
        CONTENT_DISPOSITION -> contentDisposition
      )
  }

  private def flashWarning(warning: String): Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m(warning))
  }
}
