package controllers

import play.api.mvc.Controller
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.auth.AuthorizedAction
import controllers.forms.NodeIdsForm
import models.orm.finders.NodeDocumentFinder

trait UntaggedDocumentsController extends Controller {
  val MaxPageSize = 100

  trait Storage {
    def untaggedCountsByNodeId(nodeIds: Iterable[Long], documentSetId: Long): Iterable[(Long, Int)]
  }

  val storage: Storage

  def nodeCounts(id: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(id)) { implicit request =>
    NodeIdsForm().bindFromRequest.fold(
      formWithErrors => BadRequest,
      nodeIds => {
        val counts = storage.untaggedCountsByNodeId(nodeIds, id)
        Ok(views.json.helper.nodeCounts(counts))
      }
    )
  }

}

object UntaggedDocumentsController extends UntaggedDocumentsController {
  override val storage = new Storage {
    override def untaggedCountsByNodeId(nodeIds: Iterable[Long], documentSetId: Long): Iterable[(Long, Int)] = {
      val counts = NodeDocumentFinder
        .byNodeIdsInDocumentSet(nodeIds, documentSetId)
        .untaggedDocumentCountsByNodeId
        .toMap

      nodeIds.map(nodeId => (nodeId -> counts.getOrElse(nodeId, 0l).toInt))
    }
  }
}
