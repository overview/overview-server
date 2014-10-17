package controllers

import play.api.libs.concurrent.Execution.Implicits._
import controllers.auth.{ AuthorizedAction, Authorities }
import scala.concurrent.Future
import models.archive.ArchiveEntry
import models.archive.Archive

trait DocumentSetArchiveController extends Controller {

  import Authorities._

  def archive(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)) { implicit request =>
    //    val m = views.Magic.scopedMessages("controllers.DocumentSetArchiveController")
    //    
    //    Redirect(routes.DocumentSetController.index()).flashing("error" -> m("unsupported"))

    val archive = archiver.createArchive(Seq.empty)
    
    Ok.withHeaders(
      CONTENT_TYPE -> "application/octet-stream",
      CONTENT_LENGTH -> s"${archive.size}")

  }
  
  protected val archiver: Archiver
  
  protected trait Archiver {
    def createArchive(entries: Seq[ArchiveEntry]): Archive
  }

}

object DocumentSetArchiveController extends DocumentSetArchiveController {
  
  override protected val archiver: Archiver = new Archiver {
    override def createArchive(entries: Seq[ArchiveEntry]): Archive = ???
  }
}