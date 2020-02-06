package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject
import play.api.http.{HttpChunk,HttpEntity}
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee.{Enumeratee,Enumerator,Iteratee}
import play.api.libs.json.{Json,JsObject}
import play.api.mvc.Result
import scala.collection.immutable
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
  val controllerComponents: ControllerComponents
) extends BaseController with SelectionHelpers {
  private val BatchSize = 100

  private def serveExport(
    format: Format,
    filename: String,
    documentSetId: Long,
    request: AuthorizedRequest[_],
    createRows: (MetadataSchema,Source[(Document, Vector[Long]), akka.NotUsed], Vector[Tag]) => Rows)
  : Future[Result] = {
    val futureStuff = for {
      maybeDocumentSet <- documentSetBackend.show(documentSetId)
      selectionOrResponse <- requestToSelection(documentSetId, request)(request.messages)
    } yield (maybeDocumentSet, selectionOrResponse)

    futureStuff.flatMap(_ match {
      case (None, _) => Future.successful(NotFound)
      case (_, Left(response)) => Future.successful(response)
      case (Some(documentSet), Right(selection)) => {
        val contentDisposition = ContentDisposition.fromFilename(filename).contentDisposition

        for {
          tags <- tagBackend.index(documentSetId)
        } yield {
          val documents = streamDocumentsWithTagIds(documentSetId, selection)
          val export = new Export(createRows(documentSet.metadataSchema, documents, tags), format)
          val bytes: Source[ByteString, _] = export.byteSource
          val chunks = bytes.map(HttpChunk.Chunk.apply _)

          Ok.sendEntity(HttpEntity.Chunked(chunks, Some(export.contentType)))
            .withHeaders(
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
  ) = authorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, request, DocumentsWithStringTags.apply)
  }

  def documentsWithColumnTags(
    format: Format,
    filename: String,
    documentSetId: Long
  ) = authorizedAction(userViewingDocumentSet(documentSetId)).async { implicit request =>
    serveExport(format, filename, documentSetId, request, DocumentsWithColumnTags.apply)
  }

  private def documentsWithTagIdsSource(documentSetId: Long, documentIds: Vector[Long]): Source[(Document,Vector[Long]), akka.NotUsed] = {
    val futureSource: Future[Source[(Document,Vector[Long]), akka.NotUsed]] = for {
      documents <- documentBackend.index(documentSetId, documentIds)
      documentIdToTagIds <- documentTagBackend.indexMany(documentIds)
    } yield {
      val tuples: Vector[(Document,Vector[Long])] = documents.map((d) => (d -> documentIdToTagIds(d.id)))
      Source(tuples.to[immutable.Iterable])
    }

    Source.futureSource(futureSource).mapMaterializedValue(_ => akka.NotUsed)
  }

  private def streamDocumentsWithTagIds(documentSetId: Long, selection: Selection): Source[(Document,Vector[Long]), akka.NotUsed] = {
    val futureSource = selection.getAllDocumentIds.map { documentIds =>
      val batches = documentIds.grouped(BatchSize)

      Source(batches.to[immutable.Iterable])
        .flatMapConcat(batch => documentsWithTagIdsSource(documentSetId, batch))
    }

    Source.futureSource(futureSource).mapMaterializedValue(_ => akka.NotUsed)
  }
}
