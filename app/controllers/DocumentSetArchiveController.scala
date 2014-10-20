package controllers

import play.api.libs.concurrent.Execution.Implicits._
import controllers.auth.{ AuthorizedAction, Authorities }
import scala.concurrent.Future
import models.archive.ArchiveEntry
import models.archive.Archive
import models.DocumentFileInfo
import models.archive.ArchiveEntryFactory
import org.overviewproject.tree.orm.File

trait DocumentSetArchiveController extends Controller {

  import Authorities._

  def archive(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)) { implicit request =>
    //    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")
    //    
    //    Redirect(routes.DocumentSetController.index()).flashing("error" -> m("unsupported"))
    val fileInfo = storage.findDocumentFileInfo(documentSetId)
    val archiveEntries = fileInfo.flatMap(archiveEntryFactory.create)
    
    val archive = archiver.createArchive(archiveEntries)
    
    Ok.withHeaders(
      CONTENT_TYPE -> "application/octet-stream",
      CONTENT_LENGTH -> s"${archive.size}")

  }
  
  protected val archiver: Archiver
  protected val storage: Storage
  
  protected val archiveEntryFactory: ArchiveEntryFactory
  
  protected trait Archiver {
    def createArchive(entries: Seq[ArchiveEntry]): Archive
  }
  
  protected trait Storage {
    def findDocumentFileInfo(documentSetId: Long): Seq[DocumentFileInfo]
  }

}

object DocumentSetArchiveController extends DocumentSetArchiveController {
  
  override protected val archiver: Archiver = new Archiver {
    override def createArchive(entries: Seq[ArchiveEntry]): Archive = ???
  }
  
  override protected val storage: Storage = new Storage {
    override def findDocumentFileInfo(documentSetId: Long): Seq[DocumentFileInfo] = ???
  }
  
  override protected val archiveEntryFactory: ArchiveEntryFactory = new ArchiveEntryFactory {
    override val storage = new Storage {
      override def findFile(fileId: Long): Option[File] = ???
    }
  }
}