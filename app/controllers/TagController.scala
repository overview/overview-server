package controllers

import models.{PersistentDocumentList, PersistentTag}
import play.api.db.DB
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import models.PersistentTagLoader


object TagController extends Controller {

  def create(documentSetId: Long, tagName: String, 
             nodeIds: String, tagIds: String, documentIds: String) = Action {
    DB.withTransaction { implicit connection =>
      val tag = PersistentTag.findOrCreateByName(tagName, documentSetId)

      val documents = new PersistentDocumentList(IdList(nodeIds),
    		  								     IdList(documentIds))
      
      val tagCount = documents.addTag(tag.id)

      Ok(views.json.Tag.show((tag.id, tagCount)))
    }
  }
  
  def remove(documentSetId: Long, tagName: String,
             nodeIds: String, tagIds: String, documentIds: String) = Action {
    DB.withTransaction { implicit connection => 
      
      PersistentTag.findByName(tagName, documentSetId) match {
        case Some(tag) => {
          val documents = new PersistentDocumentList(IdList(nodeIds),
                                                     IdList(documentIds))
          
          val tagCount = documents.removeTag(tag.id)
          
          Ok(views.json.Tag.show((tag.id, tagCount)))
        }
        case None => NotFound
      }
      
    }
  }
}

