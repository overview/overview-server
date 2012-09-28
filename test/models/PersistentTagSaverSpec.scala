package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class PersistentTagSaverSpec extends Specification {
	
  step(start(FakeApplication()))
  
  "PersistentTagSaver" should {
    
    "add a new tag to the database, returning id" in new DbTestContext {
      val documentSetId = insertDocumentSet("TagSaverSpec")
      val tagSaver = new PersistentTagSaver()
      val name = "a tag"
        
      val id = tagSaver.save(documentSetId, name)

      id must not beNone
      
      val tagId  = SQL("SELECT id FROM tag WHERE name = {name}").
       				on("name" -> name).as(scalar[Long] *).headOption
      
      id must be equalTo(tagId)												            
    }
    
    "return None if attempting to add already existing tag" in new DbTestContext {
      val documentSetId = insertDocumentSet("TagSaverSpec")
      val tagSaver = new PersistentTagSaver()
      val name = "a tag"

      val goodId = tagSaver.save(documentSetId, name)
      val noId = tagSaver.save(documentSetId, name)
      
      noId must beNone
    }

    "delete a tag" in new DbTestContext {
      val documentSetId = insertDocumentSet("TagSaverSpec")
      val tagName = "a tag" 
      val tagId = insertTag(documentSetId, tagName)
      val documentId = insertDocument(documentSetId, "title", "dcId")
      tagDocuments(tagId, Seq(documentId))
      
      val tagSaver = new PersistentTagSaver()
      val rowsDeleted = tagSaver.delete(tagId)

      rowsDeleted must be equalTo(2)
      
      val noTag = SQL("SELECT id FROM tag WHERE id = {tagId}").on("tagId" -> tagId).
        as(scalar[Long] *).headOption

      noTag must beNone
    }
  }
  
  step(stop)
}
