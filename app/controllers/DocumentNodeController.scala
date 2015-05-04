package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsObject,JsNumber,JsValue}
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.DocumentNodeBackend

trait DocumentNodeController extends Controller with SelectionHelpers {
  protected val documentNodeBackend: DocumentNodeBackend

  def countByNode(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    def formatCounts(counts: Map[Long,Int]): JsValue = {
      def tupleToValue(t: (Long,Int)): (String,JsValue) = (t._1.toString -> JsNumber(t._2))
      val values: Seq[(String,JsValue)] = counts.toSeq.map(tupleToValue _)
      JsObject(values)
    }

    val nodeIds = RequestData(request).getLongs("countNodes")

    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        documentNodeBackend.countByNode(selection, nodeIds)
          .map(counts => Ok(formatCounts(counts)))
      }
    })
  }
}

object DocumentNodeController extends DocumentNodeController {
  override protected val documentNodeBackend = DocumentNodeBackend
}
