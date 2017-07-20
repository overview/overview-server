package controllers

import javax.inject.Inject
import play.api.i18n.{MessagesApi,Messages}
import play.api.libs.json.{JsArray,JsNumber,JsValue}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.{DocumentBackend,HighlightBackend}
import com.overviewdocs.query.QueryParser
import com.overviewdocs.searchindex.{Highlight,Utf16Highlight}

class HighlightController @Inject() (
  documentBackend: DocumentBackend,
  highlightBackend: HighlightBackend,
  val controllerComponents: ControllerComponents
) extends BaseController {

  /** Lists highlights: .e.g, `[[2,4],[6,8]]` as UTF-16 offsets
    *
    * Security consideration: the backend must filter by document set ID,
    * because that's how we authorize this action.
    */
  def index(documentSetId: Long, documentId: Long, queryString: String) = authorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    QueryParser.parse(queryString) match {
      case Left(_) => Future.successful(BadRequest(jsonError("illegal-arguments", request.messages("com.overviewdocs.query.SyntaxError"))))
      case Right(query) => {
        for {
          highlights <- highlightBackend.highlight(documentSetId, documentId, query)
        } yield {
          // JavaScript text is UTF-16. It'll be grabbing the text on its own,
          // so we don't need to convert highlights to utf-8.
          val utf16Highlights = highlights.map(_.asInstanceOf[Utf16Highlight])
          val json = JsArray(utf16Highlights.map(h => JsArray(Seq(JsNumber(h.begin), JsNumber(h.end)))))
          Ok(json).withHeaders(CACHE_CONTROL -> "no-cache")
        }
      }
    }
  }
}
