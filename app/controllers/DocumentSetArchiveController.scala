package controllers

import play.api.libs.concurrent.Execution.Implicits._
import controllers.auth.{ AuthorizedAction, Authorities }
import scala.concurrent.Future
import models.archive.ArchiveEntry
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntryFactory
import play.api.libs.iteratee.Enumerator
import controllers.backend.DbBackend
import org.overviewproject.models.tables.Documents
import models.archive.zip.ZipArchive
import models.archive.ArchiveEntryCollection
import org.overviewproject.models.tables.Files
import models.OverviewDatabase
import org.overviewproject.models.File
import org.overviewproject.util.ContentDisposition

trait DocumentSetArchiveController extends Controller {

  import Authorities._

  def archive(documentSetId: Long, filename: String) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    //    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")
    //    
    //    Redirect(routes.DocumentSetController.index()).flashing("error" -> m("unsupported"))
    for (fileInfo <- storage.findDocumentFileInfo(documentSetId)) yield {
      val archiveEntries = fileInfo.flatMap(archiveEntryFactory.create)

      val archive = archiver.createArchive(archiveEntries)

      Ok.feed(Enumerator.fromStream(archive.stream)).
        withHeaders(
          CONTENT_TYPE -> "application/octet-stream",
          CONTENT_LENGTH -> s"${archive.size}",
          CONTENT_DISPOSITION -> ContentDisposition.fromFilename(filename).contentDisposition)
    }
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

  override protected val archiveEntryFactory: ArchiveEntryFactory = new ArchiveEntryFactory {
    import org.overviewproject.database.Slick.simple._
    
    override val storage = new Storage {
      override def findFile(fileId: Long): Option[File] = OverviewDatabase.withSlickSession { session =>
        Files.filter(f => f.id === fileId).firstOption(session)
      }

    }
  }
}