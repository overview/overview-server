package controllers

import javax.inject.Inject
import play.api.i18n.Messages
import play.api.libs.json.{JsArray,JsNumber,JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.HighlightBackend
import com.overviewdocs.query.QueryParser
import com.overviewdocs.searchindex.Highlight

class HighlightController @Inject() (
  val highlightBackend: HighlightBackend
) extends Controller {

  /** Lists highlights: .e.g, `[[2,4],[6,8]]`
    *
    * Security consideration: the backend must filter by document set ID,
    * because that's how we authorize this action.
    */
  def index(documentSetId: Long, documentId: Long, queryString: String) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    QueryParser.parse(queryString) match {
      case Left(_) => Future.successful(BadRequest(jsonError("illegal-arguments", Messages("com.overviewdocs.query.SyntaxError"))))
      case Right(query) => {
        highlightBackend.highlight(documentSetId, documentId, query).map { highlights: Seq[Highlight] => 
          val json = Highlight.asJson(highlights)
          Ok(json).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
    }
  }
}
