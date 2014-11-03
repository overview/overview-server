package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import org.overviewproject.tree.orm.{Document,DocumentSet,Tag}
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.util.ContentDisposition

import controllers.auth.{ AuthorizedAction, Authorities }
import models.OverviewDatabase
import models.export.Export
import models.export.rows._
import models.export.format.Format
import models.orm.finders.{ DocumentFinder, DocumentSetFinder, TagFinder }


trait DocumentSetExportController extends Controller {
  import Authorities._

  trait Storage {
    def findDocumentSet(id: Long): Option[DocumentSet]
    def loadDocumentsWithStringTags(documentSetId: Long): FinderResult[(Document,Option[String])]
    def loadTags(documentSetId: Long): FinderResult[Tag]
    def loadDocumentsWithTagIds(documentSetId: Long): FinderResult[(Document,Option[String])]
  }

  trait RowsCreator {
    def documentsWithStringTags(documents: FinderResult[(Document,Option[String])]) : Rows
    def documentsWithColumnTags(documents: FinderResult[(Document,Option[String])], tags: FinderResult[Tag]) : Rows
  }

  private[controllers] def createExport(rows: Rows, format: Format) = {
    new Export(rows, format)
  }

  def index(documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)) { implicit request =>
    storage.findDocumentSet(documentSetId) match {
      case Some(documentSet) => Ok(views.html.DocumentSetExport.index(documentSet))
      case None => NotFound
    }
  }

  private def serveExport(export: Export, filename: String) : Result = {
    val inputStream = OverviewDatabase.inTransaction {
      export.asFileInputStream
    }

    val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition
  
    Ok.feed(Enumerator.fromStream(inputStream))
      .withHeaders(
        CONTENT_TYPE -> export.contentType,
        CONTENT_LENGTH -> inputStream.getChannel.size.toString, // The InputStream.available API makes no guarantee
        CACHE_CONTROL -> "max-age=0",
        CONTENT_DISPOSITION -> contentDisposition
      )
  }

  def documentsWithStringTags(format: Format, filename: String, documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    Future(OverviewDatabase.inTransaction {
      val documents = storage.loadDocumentsWithStringTags(documentSetId)
      val rows = rowsCreator.documentsWithStringTags(documents)
      val export = createExport(rows, format)

      serveExport(export, filename)
    })
  }

  def documentsWithColumnTags(format: Format, filename: String, documentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    Future(OverviewDatabase.inTransaction {
      val tags = storage.loadTags(documentSetId)
      val documents = storage.loadDocumentsWithTagIds(documentSetId)
      val rows = rowsCreator.documentsWithColumnTags(documents, tags)
      val export = createExport(rows, format)

      serveExport(export, filename)
    })
  }

  protected val storage: DocumentSetExportController.Storage
  protected val rowsCreator : DocumentSetExportController.RowsCreator
}

object DocumentSetExportController extends DocumentSetExportController {
  object DatabaseStorage extends Storage {
    override def findDocumentSet(id: Long): Option[DocumentSet] = {
      DocumentSetFinder.byDocumentSet(id).headOption
    }

    override def loadDocumentsWithStringTags(documentSetId: Long) = {
      DocumentFinder.byDocumentSet(documentSetId).withTagsAsStrings
    }

    override def loadTags(documentSetId: Long) = {
      TagFinder.byDocumentSet(documentSetId)
    }

    override def loadDocumentsWithTagIds(documentSetId: Long) = {
      DocumentFinder.byDocumentSet(documentSetId).withTagsAsLongStrings
    }
  }

  object ExportRowsCreator extends RowsCreator {
    override def documentsWithStringTags(documents : FinderResult[(Document,Option[String])]) = {
      new DocumentsWithStringTags(documents)
    }

    override def documentsWithColumnTags(documents: FinderResult[(Document,Option[String])], tags: FinderResult[Tag]) = {
      new DocumentsWithColumnTags(documents, tags)
    }
  }

  override protected val storage = DatabaseStorage
  override protected val rowsCreator = ExportRowsCreator
}
