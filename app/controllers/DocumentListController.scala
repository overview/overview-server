package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend, DocumentNodeBackend, DocumentTagBackend, HighlightBackend}

trait DocumentListController extends Controller with SelectionHelpers {
  protected val documentBackend: DocumentBackend
  protected val documentNodeBackend: DocumentNodeBackend
  protected val documentTagBackend: DocumentTagBackend
  protected val highlightBackend: HighlightBackend

  private val MaxPageSize = 100

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val pr = pageRequest(request, MaxPageSize)

    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        for {
          page <- documentBackend.index(selection, pr, false)

          snippets <- highlightBackend.index(documentSetId, page.items.map(_.id), selectionRequest(documentSetId, request).right.get.q.get)

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
          Ok(views.json.DocumentList.show(selection.id, pageOfItems))
        }
      }
    })
  }
}

object DocumentListController extends DocumentListController {
  override val documentBackend = DocumentBackend
  override val documentNodeBackend = DocumentNodeBackend
  override val documentTagBackend = DocumentTagBackend
  override val highlightBackend = HighlightBackend
}
