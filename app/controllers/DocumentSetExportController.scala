package controllers

import java.io.FileInputStream
import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import controllers.auth.{ AuthorizedAction, Authorities }
import models.export._
import models.orm.finders.{ DocumentFinder, FinderResult, TagFinder }
import models.orm.Tag
import org.overviewproject.tree.orm.Document

trait DocumentSetExportController extends Controller {
  import Authorities._

  trait Storage {
    def loadDocumentsWithStringTags(documentSetId: Long): FinderResult[(Document,Option[String])]
    def loadTags(documentSetId: Long): FinderResult[Tag]
    def loadDocumentsWithTagIds(documentSetId: Long): FinderResult[(Document,Option[String])]
  }

  trait Exporters {
    def documentsWithStringTags(documents: FinderResult[(Document,Option[String])]) : Export
    def documentsWithColumnTags(documents: FinderResult[(Document,Option[String])], tags: FinderResult[Tag]) : Export
  }

  def index(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    Ok(views.html.DocumentSetExport.index(documentSetId))
  }

  private def serveFuture(future: Future[(String,FileInputStream)]) = {
    Async {
      future.map(Function.tupled { (contentTypeString: String, inputStream: FileInputStream) =>
        Ok.feed(Enumerator.fromStream(inputStream))
          .withHeaders(
            CONTENT_TYPE -> contentTypeString,
            CONTENT_LENGTH -> inputStream.getChannel.size.toString, // The InputStream.available API makes no guarantee
            CACHE_CONTROL -> "max-age=0",
            //CONTENT_DISPOSITION -> "attachment; filename=overview-export.csv"
            CONTENT_DISPOSITION -> "inline"
          )
      })
    }
  }

  def documentsWithStringTags(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val future : Future[(String,FileInputStream)] = Future {
      val documents = storage.loadDocumentsWithStringTags(documentSetId)
      val export = exporters.documentsWithStringTags(documents)
      (export.contentTypeHeader, export.exportToInputStream)
    }

    serveFuture(future)
  }

  def documentsWithColumnTags(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val future : Future[(String,FileInputStream)] = Future {
      val tags = storage.loadTags(documentSetId)
      val documents = storage.loadDocumentsWithTagIds(documentSetId)
      val export = exporters.documentsWithColumnTags(documents, tags)
      (export.contentTypeHeader, export.exportToInputStream)
    }

    serveFuture(future)
  }

  protected val storage: DocumentSetExportController.Storage
  protected val exporters : DocumentSetExportController.Exporters
}

object DocumentSetExportController extends DocumentSetExportController {
  object DatabaseStorage extends Storage {
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

  object ModelExporters extends Exporters {
    override def documentsWithStringTags(documents : FinderResult[(Document,Option[String])]) = {
      new ExportDocumentsWithStringTags(documents)
    }

    override def documentsWithColumnTags(documents: FinderResult[(Document,Option[String])], tags: FinderResult[Tag]) = {
      new ExportDocumentsWithColumnTags(documents, tags)
    }
  }

  override protected val storage = DatabaseStorage
  override protected val exporters = ModelExporters
}
