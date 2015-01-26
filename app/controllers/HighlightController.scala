package controllers

import play.api.libs.json.{JsArray,JsNumber,JsValue}
import scala.concurrent.ExecutionContext.Implicits.global

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.HighlightBackend
import org.overviewproject.searchindex.Highlight

trait HighlightController extends Controller {
  protected val highlightBackend: HighlightBackend

  /** Lists highlights: .e.g, `[[2,4],[6,8]]`
    *
    * Security consideration: the backend must filter by document set ID,
    * because that's how we authorize this action.
    */
  def index(documentSetId: Long, documentId: Long, q: String) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async {
    highlightBackend.index(documentSetId, documentId, q).map { highlights: Seq[Highlight] => 
      val json = JsArray(highlights.map { highlight =>
        JsArray(Seq(JsNumber(highlight.begin), JsNumber(highlight.end)))
      })
      Ok(json)
    }
  }
}

object HighlightController extends HighlightController {
  override protected val highlightBackend = HighlightBackend
}
