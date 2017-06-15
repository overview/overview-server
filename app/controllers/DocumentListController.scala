package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import com.overviewdocs.searchindex.Utf16Snippet
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend, DocumentNodeBackend, DocumentTagBackend, HighlightBackend, SelectionBackend}

class DocumentListController @Inject() (
  documentBackend: DocumentBackend,
  documentNodeBackend: DocumentNodeBackend,
  documentTagBackend: DocumentTagBackend,
  highlightBackend: HighlightBackend,
  protected val selectionBackend: SelectionBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) with SelectionHelpers {
  private val MaxPageSize = 100

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val pr = pageRequest(request, MaxPageSize)

    requestToSelectionWithQuery(documentSetId, request.user.email, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right((selection, sr)) => {
        for {
          page <- documentBackend.index(selection, pr, true)

          snippets <- sr.flatMap(_.q) match {
            case None => Future.successful(Map.empty[Long, Seq[Utf16Snippet]])
            case Some(q) => highlightBackend.highlights(documentSetId, page.items.map(_.id), q)
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
          Ok(views.json.DocumentList.show(selection, pageOfItems))

        }
      }
    })
  }
}
