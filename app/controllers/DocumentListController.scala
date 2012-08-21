package controllers

import java.sql.Connection
import play.api.mvc.{Action,AnyContent,Request}

import models.PersistentDocumentList
import models.orm.User

trait DocumentListController extends BaseController {
  def index(documentSetId: Long, nodeids: String, tagids: String,
              documentids: String, start: Int, end: Int) = authorizedAction(userOwningDocumentSet(documentSetId)) {
    user => (request: Request[AnyContent], connection: Connection) => {
      authorizedIndex(user, documentSetId, nodeids, tagids, documentids, start, end)(request, connection)
    }
  }

  protected def authorizedIndex(user: User, documentSetId: Long, nodeids: String, tagids: String,
                      documentids: String, start: Int, end: Int)(implicit request: Request[AnyContent], connection: Connection) = {
    val (validStart, validEnd) = SaneRange(start, end)
    
    val documents = 
      new PersistentDocumentList(documentSetId,
    		  					 IdList(nodeids), 
                                 IdList(tagids),
                                 IdList(documentids))
    val selection = documents.loadSlice(validStart, validEnd)
    val totalItems = documents.loadCount
    
    Ok(views.json.DocumentList.show(selection, totalItems))
  }
}

object DocumentListController extends DocumentListController
