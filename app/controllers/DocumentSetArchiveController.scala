package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Result
import scala.concurrent.Future

import com.overviewdocs.util.ContentDisposition
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.DocumentFileInfoBackend
import models.archive.{Archive,ArchiveEntry,ArchiveEntryCollection}
import models.archive.zip.ZipArchive

trait DocumentSetArchiveController extends Controller with SelectionHelpers {
  val MaxNumberOfEntries: Int = 0xFFFF // If more than 2 bytes are needed for entries, ZIP64 should be used
  val MaxArchiveSize: Long = 0xFFFFFFFFl // If more than 4 bytes are needed for size, ZIP64 should be used

  protected val ArchiveTooLarge = "archiveTooLarge"
  protected val TooManyEntries = "tooManyEntries"
  protected val Unsupported = "unsupported"

  def archive(documentSetId: Long, filename: String) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        for {
          documentIds <- selection.getAllDocumentIds
          documentViewInfos <- backend.indexDocumentViewInfos(documentIds)
        } yield {
          val archiveEntries = documentViewInfos.map(_.archiveEntry)

          createArchive(archiveEntries) match {
            case Left(warning) => flashWarning(warning)
            case Right(archive) => streamArchive(archive, filename)
          }
        }
      }
    })
  }

  private def createArchive(archiveEntries: Seq[ArchiveEntry]): Either[String, Archive] = {
    lazy val archive = archiver.createArchive(archiveEntries)

    if (archiveEntries.isEmpty) Left(Unsupported)
    else if (archiveEntries.length > MaxNumberOfEntries) Left(TooManyEntries)
    else if (archive.size > MaxArchiveSize) Left(ArchiveTooLarge)
    else Right(archive)
  }


  private def streamArchive(archive: Archive, filename: String): Result = {
    val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition

    Ok
      .feed(archive.stream)
      .withHeaders(
        CONTENT_TYPE -> "application/x-zip-compressed",
        CONTENT_LENGTH -> s"${archive.size}",
        CONTENT_DISPOSITION -> contentDisposition
      )
  }

  private def flashWarning(warning: String): Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m(warning))
  }


  protected val archiver: Archiver
  protected val backend: DocumentFileInfoBackend

  protected trait Archiver {
    def createArchive(entries: Seq[ArchiveEntry]): Archive
  }
}

object DocumentSetArchiveController extends DocumentSetArchiveController {

  override protected val archiver: Archiver = new Archiver {
    override def createArchive(entries: Seq[ArchiveEntry]): Archive = {
      val validEntries = new ArchiveEntryCollection(entries)
      new ZipArchive(validEntries)
    }
  }

  override protected val backend: DocumentFileInfoBackend = DocumentFileInfoBackend
}
