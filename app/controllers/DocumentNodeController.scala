package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsObject,JsNumber,JsValue}
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userViewingDocumentSet
import controllers.backend.{DocumentNodeBackend,SelectionBackend}
import controllers.backend.exceptions.SearchParseFailed
import models.IdList

trait DocumentNodeController extends Controller {
  protected val documentNodeBackend: DocumentNodeBackend
  protected val selectionBackend: SelectionBackend

  def countByNode(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    def formatCounts(counts: Map[Long,Int]): JsValue = {
      def tupleToValue(t: (Long,Int)): (String,JsValue) = (t._1.toString -> JsNumber(t._2))
      val values: Seq[(String,JsValue)] = counts.toSeq.map(tupleToValue _)
      JsObject(values)
    }

    val nodeIds: Option[Seq[Long]] = for {
      postData <- request.body.asFormUrlEncoded
      nodeIdsSeq <- postData.get("countNodes")
      nodeIdsString <- nodeIdsSeq.headOption
    } yield IdList.longs(nodeIdsString).ids

    val forceRefresh: Option[Boolean] = for {
      postData <- request.body.asFormUrlEncoded
      refreshStringSeq <- postData.get("refresh")
      refreshString <- refreshStringSeq.headOption
    } yield refreshString == "true"

    val sr = selectionRequest(documentSetId, request)

    val selectionFuture = forceRefresh match {
      case Some(true) => selectionBackend.create(request.user.email, sr)
      case _ => selectionBackend.findOrCreate(request.user.email, sr)
    }

    selectionFuture
      .flatMap(selection => documentNodeBackend.countByNode(selection, nodeIds.getOrElse(Seq())))
      .recover { case e: SearchParseFailed => Map[Long,Int]() }
      .map(counts => Ok(formatCounts(counts)))
  }
}

object DocumentNodeController extends DocumentNodeController {
  override protected val documentNodeBackend = DocumentNodeBackend
  override protected val selectionBackend = SelectionBackend
}
