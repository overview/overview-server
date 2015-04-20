package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.exceptions.SearchParseFailed
import controllers.backend.{DocumentBackend,DocumentNodeBackend,DocumentTagBackend}
import models.pagination.Page
import models.{IdList,OverviewDatabase,Selection}
import org.overviewproject.models.DocumentHeader

trait DocumentListController extends Controller with SelectionHelpers {
  protected val documentBackend: DocumentBackend
  protected val documentNodeBackend: DocumentNodeBackend
  protected val documentTagBackend: DocumentTagBackend

  private val MaxPageSize = 100

  def index(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val pr = pageRequest(request, MaxPageSize)

    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        val happyFuture = for {
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
    })
  }
}

object DocumentListController extends DocumentListController {
  override val documentBackend = DocumentBackend
  override val documentNodeBackend = DocumentNodeBackend
  override val documentTagBackend = DocumentTagBackend
}
