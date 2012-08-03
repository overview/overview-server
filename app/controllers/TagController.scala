package controllers

import models.{PersistentDocumentList, TagLoader, TagSaver}
import play.api.db.DB
import play.api.mvc.{Action, Controller}
import play.api.Play.current


object TagController extends Controller {

  def create(documentSetId: Long, tag: String, 
             nodeIds: String, tagIds: String, documentIds: String) = Action {
    DB.withTransaction { implicit connection =>

      val tagLoader = new TagLoader()

      val tagId = tagLoader.loadByName(tag) match {
        case Some(id) => id
        case None => {
          val tagSaver = new TagSaver()
          tagSaver.save(tag, documentSetId).get
        }
      }  
      
      val documents = new PersistentDocumentList(IdList(nodeIds),
    		  								     IdList(documentIds))
      
      val tagCount = documents.addTag(tagId)

      Ok(views.json.Tag.show((tagId, tagCount)))

    }
  }
}