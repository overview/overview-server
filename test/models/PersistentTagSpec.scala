package models

import org.specs2.mock._
import org.specs2.mutable.Specification

class PersistentTagSpec extends Specification with Mockito {
  implicit val unusedConnection : java.sql.Connection = null
  
  "PersistentTag" should {
    
    "be created by findOrCreateByName factory method if not in database" in {
      val loader = mock[TagLoader]
      val saver = mock[TagSaver]
      val documentSetId = 4l
      val dummyTagId = 23l
      val name = "a tag"
        
      loader loadByName(name) returns None
      saver save(name, documentSetId) returns Some(dummyTagId)
      
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, saver)
      
      there was one(loader).loadByName(name)
      there was one(saver).save(name, documentSetId)
      
      tag.id must be equalTo(dummyTagId)
    } 
    
    "be loaded by findOrCreateByName factory method if in database" in {
      val loader = mock[TagLoader]
      val saver = mock[TagSaver]
      val documentSetId = 4l
      val dummyTagId = 23l
      val name = "a tag"
        
      loader loadByName(name) returns Some(dummyTagId)
            
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, saver)
      
      there was one(loader).loadByName(name)
      there was no(saver).save(name, documentSetId)
      
      tag.id must be equalTo(dummyTagId)
      
    }
  }

}