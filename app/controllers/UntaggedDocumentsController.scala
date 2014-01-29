package controllers

import play.api.mvc.Controller
import controllers.auth.Authorities.userOwningTree
import controllers.auth.AuthorizedAction
import controllers.forms.NodeIdsForm
import models.orm.finders.NodeDocumentFinder

trait UntaggedDocumentsController extends Controller {
  val MaxPageSize = 100
  
  trait Storage {
    def untaggedCountsByNodeId(nodeIds: Iterable[Long], treeId: Long): Iterable[(Long, Int)]
  }
  
  val storage: Storage
  

  def nodeCounts(treeId: Long) = AuthorizedAction(userOwningTree(treeId)) { implicit request => 
    NodeIdsForm().bindFromRequest.fold(
      formWithErrors => BadRequest,
      nodeIds => {
        val counts = storage.untaggedCountsByNodeId(nodeIds, treeId)
        Ok(views.json.helper.nodeCounts(counts))
      }
    )
  }

}

object UntaggedDocumentsController extends UntaggedDocumentsController {
  override val storage = new Storage {
    
    override def untaggedCountsByNodeId(nodeIds: Iterable[Long], treeId: Long): Iterable[(Long, Int)] = {
      
      val counts = NodeDocumentFinder
        .byNodeIdsInTree(nodeIds, treeId)
        .untaggedDocumentCountsByNodeId
        .toMap
        
      nodeIds.map(nodeId => (nodeId -> counts.getOrElse(nodeId, 0l).toInt))
    }
  }
}