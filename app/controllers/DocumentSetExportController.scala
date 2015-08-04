package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{Json,JsObject}
import play.api.libs.streams.Streams
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.{DocumentSetBackend,TagBackend}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentSet,Tag}
import com.overviewdocs.util.ContentDisposition
import models.export.Export
import models.export.rows._
import models.export.format.Format

trait DocumentSetExportController extends Controller {
  def index(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    documentSetBackend.show(documentSetId).map(_ match {
      case Some(documentSet) => Ok(views.html.DocumentSetExport.index(documentSet))
      case None => NotFound
    })
  }

  private def serveExport(
    format: Format,
    filename: String,
    documentSetId: Long,
    createRows: (MetadataSchema,Enumerator[DocumentForCsvExport],Seq[Tag]) => Rows)
  : Future[Result] = {
    documentSetBackend.show(documentSetId).flatMap(_ match {
      case None => Future.successful(NotFound)
      case Some(documentSet) => {
        val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition
        val documents: Enumerator[DocumentForCsvExport] = storage.streamDocumentsWithTagIds(documentSetId)

        tagBackend.index(documentSetId).flatMap { tags =>
          val rows = createRows(documentSet.metadataSchema, documents, tags)
          val export = new Export(rows, format)

          export.futureFileInputStream.map { fileInputStream =>
            Ok.feed(Enumerator.fromStream(fileInputStream))
              .withHeaders(
                CONTENT_TYPE -> export.contentType,
                CONTENT_LENGTH -> fileInputStream.getChannel.size.toString, // InputStream.available makes no guarantee
                CACHE_CONTROL -> "max-age=0",
                CONTENT_DISPOSITION -> contentDisposition
              )
          }
        }
      }
    })
  }

  def documentsWithStringTags(
    format: Format,
    filename: String,
    documentSetId: Long
  ) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, DocumentsWithStringTags.apply)
  }

  def documentsWithColumnTags(
    format: Format,
    filename: String,
    documentSetId: Long
  ) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, DocumentsWithColumnTags.apply)
  }

  protected val documentSetBackend: DocumentSetBackend
  protected val tagBackend: TagBackend
  protected val storage: DocumentSetExportController.Storage
}

object DocumentSetExportController extends DocumentSetExportController {
  trait Storage {
    def streamDocumentsWithTagIds(documentSetId: Long): Enumerator[DocumentForCsvExport]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    override def streamDocumentsWithTagIds(documentSetId: Long): Enumerator[DocumentForCsvExport] = {
      import database.api._
      import slick.backend.DatabasePublisher
      import slick.jdbc.GetResult

      val query = sql"""
        SELECT
          d.supplied_id,
          d.title,
          d.text,
          COALESCE(d.url, '') AS url,
          COALESCE(d.metadata_json_text, '{}') AS metadata_json,
          COALESCE(ARRAY_AGG(dt.tag_id), '{}'::BIGINT[]) AS tag_ids
        FROM document d
        LEFT JOIN document_tag dt ON d.id = dt.document_id
        WHERE d.document_set_id = ${documentSetId}
        GROUP BY d.supplied_id, d.title, d.text, d.url, d.metadata_json_text
      """.as[DocumentForCsvExport](GetResult(r => DocumentForCsvExport(
        r.nextString(),
        r.nextString(),
        r.nextString(),
        r.nextString(),
        Json.parse(r.nextString()).as[JsObject],
        r.nextArray[Long]()
      )))

      Streams.publisherToEnumerator(database.slickDatabase.stream(query))
    }
  }

  override protected val documentSetBackend = DocumentSetBackend
  override protected val tagBackend = TagBackend
  override protected val storage = DatabaseStorage
}
