package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication


class PersistentTagLoaderSpec extends Specification {

  step(start(FakeApplication()))
  
  "PersistentTagLoader" should {
    
    "get tag id by name if it exists" in new DbTestContext {
      val tagName = "taggy"
        
      val documentSetId = insertDocumentSet("TagLoaderSpec")
      val tagId = insertTag(documentSetId, tagName)
      
      val tagLoader = new PersistentTagLoader()
      
      val foundTag = tagLoader.loadByName(tagName)
      
      foundTag must be equalTo(Some(tagId))
    }
    
    "get None if tag does not exist" in new DbTestContext {
      val tagName = "taggy"
        
      val tagLoader = new PersistentTagLoader()
      
      val missingTag = tagLoader.loadByName(tagName)
      
      missingTag must beNone
    }
    
    
  }
  
  step(stop)
}