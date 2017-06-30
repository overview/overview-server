package controllers

import akka.stream.scaladsl.{Source,SourceQueueWithComplete}
import akka.stream.OverflowStrategy
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue,Json}
import scala.concurrent.Future

import com.overviewdocs.searchindex.Utf16Snippet
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend, DocumentNodeBackend, DocumentTagBackend, HighlightBackend, SelectionBackend}
import models.{Selection,SelectionRequest}
import models.pagination.PageRequest

class DocumentListController @Inject() (
  documentBackend: DocumentBackend,
  documentNodeBackend: DocumentNodeBackend,
  documentTagBackend: DocumentTagBackend,
  highlightBackend: HighlightBackend,
  protected val selectionBackend: SelectionBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) with SelectionHelpers {
  private val MaxPageSize = 100

  private def buildResult(documentSetId: Long, selection: Selection, selectionRequest: SelectionRequest, pageRequest: PageRequest): Future[JsValue] = {
    for {
      page <- documentBackend.index(selection, pageRequest, true)

      snippets <- (page.items.nonEmpty, selectionRequest.q) match {
        case (true, Some(q)) => highlightBackend.highlights(documentSetId, page.items.map(_.id), q)
        case _ => Future.successful(Map.empty[Long, Seq[Utf16Snippet]])
      }

      // In serial so as not to bombard Postgres
      nodeIds <- documentNodeBackend.indexMany(page.items.map(_.id))
      tagIds <- documentTagBackend.indexMany(page.items.map(_.id))
    } yield {
      val pageOfItems = page.map { document => (
        document,
        nodeIds.getOrElse(document.id, Seq()),
        tagIds.getOrElse(document.id, Seq()),
        snippets.getOrElse(document.id, Seq())
      )}

      views.json.DocumentList.show(selection, selectionRequest.sortByMetadataField, pageOfItems)
    }
  }

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val pr = pageRequest(request, MaxPageSize)

    parseSelectionRequestAndSelectFast(documentSetId, request.user.email, request).flatMap(_ match {
      case SelectionHelpers.FastFailure(result) => Future.successful(result)

      case SelectionHelpers.FastSuccess(selectionRequest, selection) => {
        buildResult(documentSetId, selection, selectionRequest, pr).map(jsObj => Ok(Json.arr(jsObj)))
      }

      case params: SelectionHelpers.SlowSelectionParameters => {
        val chunks: Source[String, _] = Source.queue(1000000, OverflowStrategy.dropHead)
          .mapMaterializedValue { chunkQueue: SourceQueueWithComplete[String] =>
            chunkQueue.offer("[")
            def onProgress(progress: Double): Unit = {
              // XXX we don't guarantee these offer() calls happen in order,
              // because of multithreading. Maybe we ought to change the
              // DocumentSelection API to return a
              // Source[Progress.Sorting,InMemorySelection].
              chunkQueue.offer(Json.obj("progress" -> progress).toString)
            }

            for {
              selection <- selectSlow(params, onProgress)
              result <- buildResult(documentSetId, selection, params.selectionRequest, pr)
            } yield {
              chunkQueue.offer(result.toString + "]")
              chunkQueue.complete
            }
          }

        val result = Ok.chunked(chunks)
          .as("application/json")
          .withHeaders(CACHE_CONTROL -> "max-age=0")
        Future.successful(result)
      }
    })
  }
}
