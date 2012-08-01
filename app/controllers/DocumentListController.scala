package controllers


import play.api.db.DB
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import models.PersistentDocumentList


object DocumentListController extends Controller {
    def index(treeId: Long, nodeids: String, tagids: String,
              documentids: String, start: Int, end: Int) = Action {
      DB.withTransaction { implicit connection => 
        val documents = new PersistentDocumentList(IdList(nodeids), IdList(documentids))
        val selection = documents.loadSlice(start, end)
        val totalItems = documents.loadCount
        
        Ok(views.json.DocumentList.show(selection, totalItems))
      }
    }
}
