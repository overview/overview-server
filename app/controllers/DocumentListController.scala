package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.exceptions.SearchParseFailed
import controllers.backend.{DocumentBackend,DocumentNodeBackend,DocumentTagBackend,SelectionBackend}
import models.pagination.Page
import models.{IdList,OverviewDatabase,Selection}
import org.overviewproject.models.DocumentHeader

trait DocumentListController extends Controller {
  protected val selectionBackend: SelectionBackend
  protected val documentBackend: DocumentBackend
  protected val documentNodeBackend: DocumentNodeBackend
  protected val documentTagBackend: DocumentTagBackend

  private val MaxPageSize = 100

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val sr = selectionRequest(documentSetId, request)
    val pr = pageRequest(request, MaxPageSize)

    val selectionFuture: Future[Selection] = pr.offset match {
      case 0 => selectionBackend.create(request.user.email, sr)
      case _ => selectionBackend.findOrCreate(request.user.email, sr)
    }

    type Item = (DocumentHeader, Seq[Long], Seq[Long])

    val happyFuture = for {
      selection <- selectionFuture
      page <- documentBackend.index(selection, pr, false)
      // In serial so as not to bombard Postgres
      nodeIds <- documentNodeBackend.indexMany(page.items.map(_.id))
      tagIds <- documentTagBackend.indexMany(page.items.map(_.id))
    } yield {
      val pageOfItems = page.map { document => (
        document,
        nodeIds.getOrElse(document.id, Seq()),
        tagIds.getOrElse(document.id, Seq())
      )}
      Ok(views.json.DocumentList.show(pageOfItems))
    }

    // TODO move away from Squeryl and put this .transform() in the backend
    happyFuture
      .transform(identity(_), backend.exceptions.wrapElasticSearchException(_))
      .recover { case spf: SearchParseFailed => BadRequest(jsonError(spf.getMessage)) }
  }
}

object DocumentListController extends DocumentListController {
  override val documentBackend = DocumentBackend
  override val documentNodeBackend = DocumentNodeBackend
  override val documentTagBackend = DocumentTagBackend
  override val selectionBackend = SelectionBackend
}
