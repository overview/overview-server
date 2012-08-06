package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PersistentTagSpec extends Specification with Mockito {
  implicit val unusedConnection : java.sql.Connection = null
  
  "PersistentTag" should {
	
    trait MockComponents extends Scope {
      val loader = mock[PersistentTagLoader]
      val saver = mock[PersistentTagSaver]
      val documentSetId = 4l
      val dummyTagId = 23l
      val name = "a tag"
      
    }
    
    
    "be created by findOrCreateByName factory method if not in database" in new MockComponents {
        
      loader loadByName(name) returns None
      saver save(name, documentSetId) returns Some(dummyTagId)
      
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, saver)
      
      there was one(loader).loadByName(name)
      there was one(saver).save(name, documentSetId)
      
      tag.id must be equalTo(dummyTagId)
    } 
    
    "be loaded by findOrCreateByName factory method if in database" in new MockComponents {
      loader loadByName(name) returns Some(dummyTagId)
            
      val tag = PersistentTag.findOrCreateByName(name, documentSetId, loader, saver)
      
      there was one(loader).loadByName(name)
      there was no(saver).save(name, documentSetId)
      
      tag.id must be equalTo(dummyTagId)
      
    }
    
    "be loaded by findByName if in database" in new MockComponents {
      loader loadByName(name) returns Some(dummyTagId)
      
      val tag = PersistentTag.findByName(name, documentSetId, loader, saver)
      
      tag must beSome
      tag.get.id must be equalTo(dummyTagId)
    }
    
    "return None from findByName if tag is not in database" in new MockComponents {
      loader loadByName(name) returns None
      
      val tag = PersistentTag.findByName(name, documentSetId, loader, saver)
      
      tag must beNone
    }
  }

}