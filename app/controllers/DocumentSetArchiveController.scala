package controllers

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import org.overviewproject.models.tables.Documents
import org.overviewproject.util.ContentDisposition
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.backend.DbBackend
import models.DocumentFileInfo
import models.archive.Archive
import models.archive.ArchiveEntry
import models.archive.ArchiveEntryCollection
import models.archive.ArchiveEntryFactory
import models.archive.ArchiveEntryFactoryWithStorage
import models.archive.zip.ZipArchive
import play.api.mvc.Result

trait DocumentSetArchiveController extends Controller {

  import Authorities._

  def archive(documentSetId: Long, filename: String) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    for (fileInfo <- storage.findDocumentFileInfo(documentSetId)) yield {
      val archiveEntries = fileInfo.flatMap(archiveEntryFactory.create)

      if (archiveEntries.nonEmpty) streamArchive(archiveEntries, filename)
      else flashUnsupportedWarning
    }
  }

  private def streamArchive(archiveEntries: Seq[ArchiveEntry], filename: String): Result = {
    val archive = archiver.createArchive(archiveEntries)

    Ok.feed(Enumerator.fromStream(archive.stream)).
      withHeaders(
        CONTENT_TYPE -> "application/octet-stream",
        CONTENT_LENGTH -> s"${archive.size}",
        CONTENT_DISPOSITION -> ContentDisposition.fromFilename(filename).contentDisposition)
  }

  private def flashUnsupportedWarning: Result = {
    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")

    Redirect(routes.DocumentSetController.index()).flashing("warning" -> m("unsupported"))
  }

  protected val archiver: Archiver
  protected val storage: Storage

  protected val archiveEntryFactory: ArchiveEntryFactory

  protected trait Archiver {
    def createArchive(entries: Seq[ArchiveEntry]): Archive
  }

  protected trait Storage {
    def findDocumentFileInfo(documentSetId: Long): Future[Seq[DocumentFileInfo]]
  }

}

object DocumentSetArchiveController extends DocumentSetArchiveController {

  override protected val archiver: Archiver = new Archiver {
    override def createArchive(entries: Seq[ArchiveEntry]): Archive = {
      val validEntries = new ArchiveEntryCollection(entries)
      new ZipArchive(validEntries)
    }
  }

  override protected val storage: Storage = new Storage with DbBackend {
    import org.overviewproject.database.Slick.simple._

    override def findDocumentFileInfo(documentSetId: Long): Future[Seq[DocumentFileInfo]] = db { session =>
      val fileInfoQuery = Documents
        .filter(_.documentSetId === documentSetId)
        .map(d => (d.title, d.fileId, d.pageId, d.pageNumber))

      fileInfoQuery.list(session).map(DocumentFileInfo.tupled)
    }
  }

  override protected val archiveEntryFactory: ArchiveEntryFactory = new ArchiveEntryFactoryWithStorage
}