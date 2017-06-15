package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumeratee,Enumerator,Iteratee}
import play.api.libs.json.{Json,JsObject}
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.{DocumentBackend,DocumentSetBackend,DocumentTagBackend,TagBackend,SelectionBackend}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{Document,DocumentSet,Tag}
import com.overviewdocs.util.ContentDisposition
import models.export.Export
import models.export.rows._
import models.export.format.Format
import models.Selection

class DocumentSetExportController @Inject() (
  documentBackend: DocumentBackend,
  documentSetBackend: DocumentSetBackend,
  documentTagBackend: DocumentTagBackend,
  tagBackend: TagBackend,
  protected val selectionBackend: SelectionBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) with SelectionHelpers {
  private val BatchSize = 100

  private def serveExport(
    format: Format,
    filename: String,
    documentSetId: Long,
    request: AuthorizedRequest[_],
    createRows: (MetadataSchema,Enumerator[(Document,Seq[Long])],Seq[Tag]) => Rows)
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
          documents <- streamDocumentsWithTagIds(documentSetId, selection)
        } yield {
          val export = new Export(createRows(documentSet.metadataSchema, documents, tags), format)
          val bytes: Enumerator[Array[Byte]] = export.bytes

          Ok.feed(bytes)
            .withHeaders(
              CONTENT_TYPE -> export.contentType,
              CACHE_CONTROL -> "max-age=0",
              CONTENT_DISPOSITION -> contentDisposition
            )
        }
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

  private def batch[A](n: Int): Enumeratee[A,Seq[A]] = {
    Enumeratee.grouped(Enumeratee.take(n).transform(Iteratee.getChunks[A]))
  }

  private def unbatch[A]: Enumeratee[Seq[A],A] = {
    Enumeratee.mapConcat[Seq[A]](identity)
  }

  private def documentsWithTagIdsEnumeratee(documentSetId: Long): Enumeratee[Seq[Long],Seq[(Document,Seq[Long])]] = {
    Enumeratee.mapM { documentIds: Seq[Long] =>
      for {
        documents <- documentBackend.index(documentSetId, documentIds)
        documentIdToTagIds <- documentTagBackend.indexMany(documentIds)
      } yield documents.map((d) => (d -> documentIdToTagIds(d.id)))
    }
  }

  private def streamDocumentsWithTagIds(documentSetId: Long, selection: Selection): Future[Enumerator[(Document,Seq[Long])]] = {
    selection.getAllDocumentIds.map { documentIds =>
      Enumerator(documentIds: _*)
        .through(batch(BatchSize))
        .through(documentsWithTagIdsEnumeratee(documentSetId))
        .through(unbatch[(Document,Seq[Long])])
    }
  }
}
