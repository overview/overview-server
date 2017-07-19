package controllers

import javax.inject.Inject
import play.api.i18n.MessagesApi
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsObject,JsNumber,JsValue}
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.{DocumentNodeBackend,SelectionBackend}

class DocumentNodeController @Inject() (
  documentNodeBackend: DocumentNodeBackend,
  protected val selectionBackend: SelectionBackend,
  val controllerComponents: ControllerComponents
) extends BaseController with SelectionHelpers {

  def countByNode(documentSetId: Long) = authorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    def formatCounts(counts: Map[Long,Int]): JsValue = {
      def tupleToValue(t: (Long,Int)): (String,JsValue) = (t._1.toString -> JsNumber(t._2))
      val values: Seq[(String,JsValue)] = counts.toSeq.map(tupleToValue _)
      JsObject(values)
    }

    val nodeIds = RequestData(request).getLongs("countNodes")

    requestToSelection(documentSetId, request)(request.messages).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        documentNodeBackend.countByNode(selection, nodeIds)
          .map(counts => Ok(formatCounts(counts)))
      }
    })
  }
}
