package controllers

import play.api.mvc.Result
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{Json,JsObject}
import play.api.libs.streams.Streams
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.{DocumentSetBackend,TagBackend}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{DocumentSet,Tag}
import com.overviewdocs.util.ContentDisposition
import models.export.Export
import models.export.rows._
import models.export.format.Format
import models.Selection

trait DocumentSetExportController extends Controller with SelectionHelpers {
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
    request: AuthorizedRequest[_],
    createRows: (MetadataSchema,Enumerator[DocumentForCsvExport],Seq[Tag]) => Rows)
  : Future[Result] = {
    val futureStuff = for {
      maybeDocumentSet <- documentSetBackend.show(documentSetId)
      selectionOrResponse <- requestToSelection(documentSetId, request)
    } yield (maybeDocumentSet, selectionOrResponse)

    futureStuff.flatMap(_ match {
      case (None, _) => Future.successful(NotFound)
      case (_, Left(response)) => Future.successful(response)
      case (Some(documentSet), Right(selection)) => {
        val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition

        for {
          tags <- tagBackend.index(documentSetId)
          documents <- storage.streamDocumentsWithTagIds(selection)
          export <- Future.successful(new Export(createRows(documentSet.metadataSchema, documents, tags), format))
          byteStream <- export.futureFileInputStream // FileInputStream so we can find size
        } yield Ok.feed(Enumerator.fromStream(byteStream))
          .withHeaders(
            CONTENT_TYPE -> export.contentType,
            CONTENT_LENGTH -> byteStream.getChannel.size.toString, // InputStream.available makes no guarantee
            CACHE_CONTROL -> "max-age=0",
            CONTENT_DISPOSITION -> contentDisposition
          )
      }
    })
  }

  def documentsWithStringTags(
    format: Format,
    filename: String,
    documentSetId: Long
  ) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, request, DocumentsWithStringTags.apply)
  }

  def documentsWithColumnTags(
    format: Format,
    filename: String,
    documentSetId: Long
  ) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, request, DocumentsWithColumnTags.apply)
  }

  protected val documentSetBackend: DocumentSetBackend
  protected val tagBackend: TagBackend
  protected val storage: DocumentSetExportController.Storage
}

object DocumentSetExportController extends DocumentSetExportController {
  trait Storage {
    def streamDocumentsWithTagIds(selection: Selection): Future[Enumerator[DocumentForCsvExport]]
  }

  object DatabaseStorage extends Storage with HasDatabase {
    override def streamDocumentsWithTagIds(selection: Selection): Future[Enumerator[DocumentForCsvExport]] = {
      for {
        ids <- selection.getAllDocumentIds
      } yield documentIdsToEnumerator(ids)
    }

    def documentIdsToEnumerator(ids: Seq[Long]): Enumerator[DocumentForCsvExport] = {
      import database.api._
      import slick.backend.DatabasePublisher
      import slick.jdbc.GetResult

      val idsAsSqlTuples: Seq[String] = ids.map((id: Long) => s"($id)")
      // The GROUP BY is the killer here.
      // TODO Paginate through the IDs as we do in ApiDocumentController.
      val query = sql"""
        WITH selection AS (
          SELECT *
          FROM (VALUES #${idsAsSqlTuples.mkString(",")})
            AS t(document_id)
        )
        SELECT
          d.supplied_id,
          d.title,
          d.text,
          COALESCE(d.url, '') AS url,
          COALESCE(d.metadata_json_text, '{}') AS metadata_json,
          COALESCE(ARRAY_AGG(dt.tag_id), '{}'::BIGINT[]) AS tag_ids
        FROM document d
        LEFT JOIN document_tag dt ON d.id = dt.document_id
        WHERE EXISTS (SELECT 1 FROM selection WHERE document_id = d.id)
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
