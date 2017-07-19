package controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject
import play.api.http.HttpEntity
import play.api.i18n.Messages
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Result
import scala.concurrent.Future

import com.overviewdocs.util.ContentDisposition
import controllers.auth.AuthConfig
import controllers.auth.Authorities.userCanExportDocumentSet
import controllers.backend.{ArchiveEntryBackend,SelectionBackend}
import models.ArchiveEntry
import models.archive.{ArchiveFactory,ZipArchive}

class DocumentSetArchiveController @Inject() (
  archiveEntryBackend: ArchiveEntryBackend,
  archiveFactory: ArchiveFactory,
  protected val selectionBackend: SelectionBackend,
  val controllerComponents: ControllerComponents,
  authConfig: AuthConfig,
  materializer: Materializer
) extends BaseController with SelectionHelpers {

  def archive(documentSetId: Long, filename: String) = authorizedAction(userCanExportDocumentSet(authConfig, documentSetId)).async { implicit request =>
    requestToSelection(documentSetId, request)(request.messages).flatMap(_ match {
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

    Ok.sendEntity(HttpEntity.Streamed(archive.stream()(materializer), Some(archive.size), Some("application/zip")))
      .withHeaders(
        CONTENT_DISPOSITION -> contentDisposition
      )
  }

  private def flashWarning(warning: String)(implicit messages: Messages): Result = {
    Redirect(routes.DocumentSetController.index()).flashing("warning" -> messages("controllers.DocumentSetArchiveController." + warning))
  }
}
