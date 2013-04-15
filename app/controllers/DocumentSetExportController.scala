package controllers

import java.io.FileInputStream
import play.api.mvc.Controller
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import controllers.auth.{ AuthorizedAction, Authorities }
import models.export.{ Export, ExportDocumentsWithStringTags }
import models.orm.finders.DocumentFinder
import models.orm.finders.FinderResult
import org.overviewproject.tree.orm.Document

trait DocumentSetExportController extends Controller {
  import Authorities._

  trait Storage {
    def loadDocumentsWithStringTags(documentSetId: Long): FinderResult[(Document,Option[String])]
  }

  trait Exporters {
    def documentsWithStringTags(documents: FinderResult[(Document,Option[String])]) : Export
  }

  def index(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    Ok(views.html.DocumentSetExport.index(documentSetId))
  }

  def documentsWithStringTags(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val promise : Future[(String,FileInputStream)] = Future {
      val documents = storage.loadDocumentsWithStringTags(documentSetId)
      val export = exporters.documentsWithStringTags(documents)
      (export.contentTypeHeader, export.exportToInputStream)
    }

    Async {
      promise.map(Function.tupled { (contentTypeString: String, inputStream: FileInputStream) =>
        Ok.feed(Enumerator.fromStream(inputStream))
          .withHeaders(
            CONTENT_TYPE -> contentTypeString,
            CONTENT_LENGTH -> inputStream.getChannel.size.toString, // The InputStream.available API makes no guarantee
            CACHE_CONTROL -> "max-age=0",
            CONTENT_DISPOSITION -> "attachment; filename=overview-export.csv"
          )
      })
    }
  }

  protected val storage: DocumentSetExportController.Storage
  protected val exporters : DocumentSetExportController.Exporters
}

object DocumentSetExportController extends DocumentSetExportController {
  object DatabaseStorage extends Storage {
    override def loadDocumentsWithStringTags(documentSetId: Long) = {
      DocumentFinder.byDocumentSet(documentSetId).withTagsAsStrings
    }
  }

  object ModelExporters extends Exporters {
    override def documentsWithStringTags(documents : FinderResult[(Document,Option[String])]) = {
      new ExportDocumentsWithStringTags(documents)
    }
  }

  override protected val storage = DatabaseStorage
  override protected val exporters = ModelExporters
}
