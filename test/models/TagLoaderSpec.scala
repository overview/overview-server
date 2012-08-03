package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication


class TagLoaderSpec extends Specification {

  step(start(FakeApplication()))
  
  "TagLoader" should {
    
    "get tag id by name if it exists" in new DbTestContext {
      val tagName = "taggy"
        
      val documentSetId = insertDocumentSet("TagLoaderSpec")
      val tagId = insertTag(documentSetId, tagName)
      
      val tagLoader = new TagLoader()
      
      val foundTag = tagLoader.getByName(tagName)
      
      foundTag must be equalTo(tagId)
    }
  }
  
  step(stop)
}